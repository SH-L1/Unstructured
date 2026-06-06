"""Fetch current Asan spatial reference datasets from public APIs.

The script calls data.go.kr standard-data APIs with the configured serviceKey,
filters rows for Asan-si, and writes normalized CSV files consumed by
sync_spatial_sources.py. SGIS boundary download is supported only when a direct
boundary API URL and SGIS credentials/token are configured.
"""

from __future__ import annotations

import csv
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable

from dotenv import load_dotenv

from spatial_data_common import clean, data_go_kr_service_key, fetch_sgis_json


load_dotenv()


ASAN_MARKERS = ("아산시",)
LOCATION_FIELD_NAMES = {
    "rdnmadr",
    "lnmadr",
    "address",
    "institutionnm",
    "instt_nm",
    "signgunm",
    "ctprvnnm",
    "mnginstnm",
    "mng_inst_nm",
}
DEFAULT_NUM_ROWS = int(os.getenv("SPATIAL_PUBLIC_API_PAGE_SIZE", "1000"))
DEFAULT_MAX_PAGES = int(os.getenv("SPATIAL_PUBLIC_API_MAX_PAGES", "300"))


@dataclass(frozen=True)
class PublicApiSource:
    name: str
    env_name: str
    output_path: str
    endpoints: tuple[str, ...]
    mapper: Callable[[dict[str, Any]], dict[str, Any]]
    query_params: tuple[dict[str, str], ...] = ({},)


def output_path(env_name: str, default_path: str) -> str:
    return clean(os.getenv(env_name)) or default_path


def request_url(url: str, params: dict[str, Any]) -> bytes:
    parsed = urllib.parse.urlparse(url)
    query = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
    query.update({key: str(value) for key, value in params.items() if value is not None})
    request = urllib.request.Request(
        urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query))),
        headers={"User-Agent": "asan-complaint-spatial-ingestor/1.0"},
    )
    timeout = int(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", "20"))
    attempts = int(os.getenv("PROVIDER_MAX_ATTEMPTS", "3"))
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                return response.read()
        except (urllib.error.URLError, TimeoutError) as exc:
            last_error = exc
            if attempt == attempts:
                break
            time.sleep(float(os.getenv("PROVIDER_RETRY_BASE_SECONDS", "1")) * attempt)
    raise RuntimeError(f"API request failed after {attempts} attempts: {url}") from last_error


def decode_payload(payload: bytes) -> Any:
    text = payload.decode("utf-8-sig", errors="replace").strip()
    if not text:
        return {}
    if text.startswith("{") or text.startswith("["):
        return json.loads(text)
    root = ET.fromstring(text)
    return xml_to_dict(root)


def xml_to_dict(element: ET.Element) -> dict[str, Any] | str:
    children = list(element)
    if not children:
        return element.text or ""
    result: dict[str, Any] = {}
    for child in children:
        value = xml_to_dict(child)
        tag = child.tag.split("}")[-1]
        if tag in result:
            existing = result[tag]
            if not isinstance(existing, list):
                result[tag] = [existing]
            result[tag].append(value)
        else:
            result[tag] = value
    return result


def deep_find_items(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        if all(isinstance(item, dict) for item in value):
            return value
        rows: list[dict[str, Any]] = []
        for item in value:
            rows.extend(deep_find_items(item))
        return rows
    if not isinstance(value, dict):
        return []
    for key in ("items", "item", "row", "rows", "data", "list"):
        child = value.get(key)
        if child is not None:
            rows = deep_find_items(child)
            if rows:
                return rows
    for child in value.values():
        rows = deep_find_items(child)
        if rows:
            return rows
    return []


def deep_find_int(value: Any, key_name: str) -> int | None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key.lower() == key_name.lower():
                try:
                    return int(str(child).replace(",", ""))
                except (TypeError, ValueError):
                    return None
            found = deep_find_int(child, key_name)
            if found is not None:
                return found
    if isinstance(value, list):
        for child in value:
            found = deep_find_int(child, key_name)
            if found is not None:
                return found
    return None


def pick(row: dict[str, Any], *names: str) -> str | None:
    lowered = {str(key).strip().lower(): value for key, value in row.items()}
    for name in names:
        for key in (name, name.lower()):
            value = clean(row.get(key) if key in row else lowered.get(key))
            if value:
                return value
    return None


def asan_row(row: dict[str, Any]) -> bool:
    searchable = " ".join(
        str(value)
        for key, value in row.items()
        if value is not None and str(key).replace("_", "").lower() in {name.replace("_", "") for name in LOCATION_FIELD_NAMES}
    )
    return any(marker in searchable for marker in ASAN_MARKERS)


def to_float_text(row: dict[str, Any], *names: str) -> str | None:
    value = pick(row, *names)
    if not value:
        return None
    try:
        return str(float(value.replace(",", "")))
    except ValueError:
        return None


def map_park(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "facility_name": pick(row, "parkNm", "PARK_NM", "공원명", "공원명칭", "facility_name"),
        "external_id": pick(row, "mngNo", "MANAGE_NO", "관리번호", "id"),
        "address": pick(row, "rdnmadr", "lnmadr", "RDNMADR", "LNMADR", "소재지도로명주소", "소재지지번주소", "address"),
        "admin_name": pick(row, "institutionNm", "INSTITUTION_NM", "instt_nm", "제공기관명", "관리기관명", "admin_name"),
        "manager": pick(row, "institutionNm", "INSTITUTION_NM", "관리기관명", "제공기관명", "manager"),
        "phone": pick(row, "phoneNumber", "PHONE_NUMBER", "전화번호", "phone"),
        "operation_status": pick(row, "referenceDate", "REFERENCE_DATE", "데이터기준일자"),
        "latitude": to_float_text(row, "latitude", "LATITUDE", "위도", "lat"),
        "longitude": to_float_text(row, "longitude", "LONGITUDE", "경도", "lon", "lng"),
    }


def map_parking_lot(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "facility_name": pick(row, "prkplceNm", "PRKPLC_NM", "주차장명", "facility_name"),
        "external_id": pick(row, "prkplceNo", "PRKPLC_NO", "주차장관리번호", "관리번호", "id"),
        "address": pick(row, "rdnmadr", "lnmadr", "RDNMADR", "LNMADR", "소재지도로명주소", "소재지지번주소", "address"),
        "admin_name": pick(row, "institutionNm", "INSTITUTION_NM", "instt_nm", "제공기관명", "관리기관명", "admin_name"),
        "manager": pick(row, "institutionNm", "INSTITUTION_NM", "관리기관명", "제공기관명", "manager"),
        "phone": pick(row, "phoneNumber", "PHONE_NUMBER", "전화번호", "phone"),
        "operation_status": pick(row, "operDay", "OPER_DAY", "REFERENCE_DATE", "운영요일", "데이터기준일자"),
        "latitude": to_float_text(row, "latitude", "LATITUDE", "위도", "lat"),
        "longitude": to_float_text(row, "longitude", "LONGITUDE", "경도", "lon", "lng"),
    }


def map_cctv(row: dict[str, Any]) -> dict[str, Any]:
    name = pick(row, "cctvNm", "CCTV_NM", "cctv명칭", "설치목적구분", "PURPS_SE", "facility_name")
    address = pick(row, "rdnmadr", "lnmadr", "RDNMADR", "LNMADR", "소재지도로명주소", "소재지지번주소", "address")
    return {
        "facility_name": name or address,
        "external_id": pick(row, "mngNo", "MANAGE_NO", "관리번호", "id"),
        "address": address,
        "admin_name": pick(row, "institutionNm", "INSTITUTION_NM", "MNG_INST_NM", "제공기관명", "관리기관명", "admin_name"),
        "manager": pick(row, "institutionNm", "INSTITUTION_NM", "MNG_INST_NM", "관리기관명", "제공기관명", "manager"),
        "phone": pick(row, "phoneNumber", "PHONE_NUMBER", "MNG_INST_TELNO", "전화번호", "phone"),
        "operation_status": pick(row, "referenceDate", "REFERENCE_DATE", "데이터기준일자"),
        "latitude": to_float_text(row, "latitude", "LATITUDE", "위도", "lat"),
        "longitude": to_float_text(row, "longitude", "LONGITUDE", "경도", "lon", "lng"),
    }


def map_parking_restriction(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "restriction_id": pick(row, "mngNo", "MANAGE_NO", "관리번호", "restriction_id", "id"),
        "road_name": pick(row, "rdnmadr", "RDNMADR", "roadNm", "도로명", "노선명", "road_name"),
        "detail_location": pick(row, "descLc", "DESC_LC", "detailLocation", "상세위치", "구간", "address"),
        "restriction_type": pick(row, "prkstopPrhibtSeCode", "PRKSTOP_PRHIBT_SE_CODE", "prhsmkType", "제한유형", "단속유형", "restriction_type"),
        "weekday_time": pick(row, "weekdayPrhibtOpenHhmm", "WEEKDAY_PRHIBT_OPEN_HHMM", "weekdayOperOpenHhmm", "평일운영시작시각", "평일단속시간", "weekday_time"),
        "saturday_time": pick(row, "satHhmmOpenHhmm", "SAT_HHMM_OPEN_HHMM", "satOperOpenHhmm", "토요일운영시작시각", "토요일단속시간", "saturday_time"),
        "holiday_time": pick(row, "holidayPrhibtOpenHhmm", "HOLIDAY_PRHIBT_OPEN_HHMM", "holidayOperOpenHhmm", "공휴일운영시작시각", "공휴일단속시간", "holiday_time"),
        "manager": pick(row, "institutionNm", "INSTITUTION_NM", "관리기관명", "제공기관명", "manager"),
        "phone": pick(row, "institutionPhoneNumber", "INSTITUTION_PHONE_NUMBER", "phoneNumber", "전화번호", "phone"),
        "latitude": to_float_text(row, "latitude", "LATITUDE", "위도", "lat"),
        "longitude": to_float_text(row, "longitude", "LONGITUDE", "경도", "lon", "lng"),
    }


SOURCES = (
    PublicApiSource(
        "parks",
        "SPATIAL_PARKS_CSV",
        "data/spatial/asan_parks.csv",
        (
            "http://api.data.go.kr/openapi/tn_pubr_public_cty_park_info_api",
            "https://api.data.go.kr/openapi/tn_pubr_public_cty_park_info_api",
        ),
        map_park,
        ({},),
    ),
    PublicApiSource(
        "parking_lots",
        "SPATIAL_PARKING_LOTS_CSV",
        "data/spatial/asan_parking_lots.csv",
        (
            "http://api.data.go.kr/openapi/tn_pubr_prkplce_info_api",
            "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api",
        ),
        map_parking_lot,
        ({"instt_code": "4520000"},),
    ),
    PublicApiSource(
        "cctv",
        "SPATIAL_CCTV_CSV",
        "data/spatial/asan_cctv.csv",
        (
            "https://api.data.go.kr/openapi/tn_pubr_public_cctv_api",
            "https://apis.data.go.kr/1741000/StanCctv/getStanCctvList",
        ),
        map_cctv,
        ({"instt_code": "4520000"},),
    ),
    PublicApiSource(
        "parking_restrictions",
        "SPATIAL_PARKING_RESTRICTIONS_CSV",
        "data/spatial/asan_parking_restrictions.csv",
        (
            "http://api.data.go.kr/openapi/tn_pubr_public_prkstop_prhibt_area_api",
            "https://api.data.go.kr/openapi/tn_pubr_public_prkstop_prhibt_area_api",
        ),
        map_parking_restriction,
        ({"SIGNGU_NM": "아산시"}, {"CTPRVN_NM": "충청남도", "SIGNGU_NM": "아산시"}),
    ),
)


def fetch_rows(endpoint: str, service_key: str, extra_params: dict[str, str]) -> tuple[list[dict[str, Any]], int | None]:
    rows: list[dict[str, Any]] = []
    total_count: int | None = None
    for page_no in range(1, DEFAULT_MAX_PAGES + 1):
        payload = request_url(
            endpoint,
            {
                "serviceKey": service_key,
                "pageNo": page_no,
                "numOfRows": DEFAULT_NUM_ROWS,
                "type": "json",
                **extra_params,
            },
        )
        decoded = decode_payload(payload)
        page_rows = deep_find_items(decoded)
        if total_count is None:
            total_count = deep_find_int(decoded, "totalCount")
        if not page_rows:
            break
        rows.extend(page_rows)
        if total_count is not None and len(rows) >= total_count:
            break
        if len(page_rows) < DEFAULT_NUM_ROWS:
            break
    return rows, total_count


def write_csv(path: str, rows: list[dict[str, Any]]) -> None:
    file_path = Path(path)
    file_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = sorted({key for row in rows for key in row.keys()})
    with file_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def fetch_source(source: PublicApiSource, service_key: str) -> str:
    last_error: Exception | None = None
    seen_keys: set[str] = set()
    merged: list[dict[str, Any]] = []
    total_count = 0
    for endpoint in source.endpoints:
        for params in source.query_params:
            try:
                raw_rows, query_total = fetch_rows(endpoint, service_key, params)
                total_count += query_total or len(raw_rows)
                for row in raw_rows:
                    if not asan_row(row):
                        continue
                    dedupe_key = json.dumps(row, ensure_ascii=False, sort_keys=True)
                    if dedupe_key in seen_keys:
                        continue
                    seen_keys.add(dedupe_key)
                    merged.append(source.mapper(row))
            except (RuntimeError, urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError, ET.ParseError, TimeoutError) as exc:
                last_error = exc
        if merged:
            target = output_path(source.env_name, source.output_path)
            write_csv(target, merged)
            return f"{source.name}: endpoint={endpoint} queried_total={total_count} asan_rows={len(merged)} output={target}"
    if not merged:
        target = output_path(source.env_name, source.output_path)
        write_csv(target, [])
        return f"{source.name}: no Asan rows returned by configured endpoint/query candidates output={target}"
    raise RuntimeError(f"{source.name}: all endpoint candidates failed") from last_error


def fetch_sgis_boundary() -> str:
    configured_boundary = clean(os.getenv("SPATIAL_ADMIN_BOUNDARIES_GEOJSON"))
    url = clean(os.getenv("SGIS_ADMIN_BOUNDARY_URL"))
    if not url and configured_boundary and configured_boundary.startswith(("http://", "https://")):
        url = configured_boundary
    target = (
        clean(os.getenv("SPATIAL_ADMIN_BOUNDARIES_OUTPUT_GEOJSON"))
        or (configured_boundary if configured_boundary and not configured_boundary.startswith(("http://", "https://")) else None)
        or "data/spatial/asan_admin_boundaries.geojson"
    )
    if not url:
        return "sgis_boundaries: skipped; set SGIS_ADMIN_BOUNDARY_URL after confirming the SGIS boundary endpoint for Asan"
    data = fetch_sgis_json(url)
    if "features" not in data:
        for key in ("result", "data"):
            nested = data.get(key)
            if isinstance(nested, dict) and "features" in nested:
                data = nested
                break
    if "features" not in data:
        raise RuntimeError("sgis_boundaries: SGIS response is not GeoJSON FeatureCollection-compatible; expected a top-level features array")
    Path(target).parent.mkdir(parents=True, exist_ok=True)
    Path(target).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return f"sgis_boundaries: output={target}"


def main() -> None:
    service_key = data_go_kr_service_key()
    if service_key:
        service_key = urllib.parse.unquote(service_key)
        for source in SOURCES:
            print(fetch_source(source, service_key))
    else:
        print("data.go.kr spatial sources: skipped; set SPATIAL_DATA_GO_KR_SERVICE_KEY or an existing public-data API key")
    print(fetch_sgis_boundary())


if __name__ == "__main__":
    main()
