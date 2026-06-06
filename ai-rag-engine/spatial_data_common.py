"""Shared helpers for Asan spatial data mart ingestion."""

from __future__ import annotations

import csv
import hashlib
import json
import os
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

from dotenv import load_dotenv


load_dotenv()


def connection_kwargs() -> dict[str, object]:
    user = os.getenv("WORKER_DB_USER") or os.getenv("DB_USER")
    password = os.getenv("WORKER_DB_PASSWORD") or os.getenv("DB_PASSWORD")
    if not user or not password:
        raise RuntimeError("WORKER_DB_USER/WORKER_DB_PASSWORD or DB_USER/DB_PASSWORD are required")
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": user,
        "password": password,
        "connect_timeout": 10,
    }


def utc_now() -> datetime:
    return datetime.utcnow()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def clean(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text if text else None


def pick(row: dict[str, Any], names: Iterable[str]) -> str | None:
    for name in names:
        if name in row:
            value = clean(row.get(name))
            if value:
                return value
    lowered = {str(key).strip().lower(): value for key, value in row.items()}
    for name in names:
        value = clean(lowered.get(name.lower()))
        if value:
            return value
    return None


def to_float(value: Any) -> float | None:
    text = clean(value)
    if text is None:
        return None
    try:
        return float(text.replace(",", ""))
    except ValueError:
        return None


def to_int(value: Any, default: int) -> int:
    text = clean(value)
    if text is None:
        return default
    try:
        return int(text)
    except ValueError:
        return default


def read_csv_records(path: str | Path) -> list[dict[str, str]]:
    file_path = Path(path)
    if not file_path.exists():
        raise FileNotFoundError(f"Spatial CSV file does not exist: {file_path}")
    last_error: Exception | None = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            with file_path.open("r", encoding=encoding, newline="") as handle:
                return list(csv.DictReader(handle))
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"Could not decode spatial CSV file: {file_path}") from last_error


def read_json(path: str | Path) -> dict[str, Any]:
    file_path = Path(path)
    if not file_path.exists():
        raise FileNotFoundError(f"Spatial JSON file does not exist: {file_path}")
    with file_path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def fetch_json(url: str, api_key: str | None = None, key_param: str = "serviceKey") -> dict[str, Any]:
    parsed = urllib.parse.urlparse(url)
    query = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
    if api_key and key_param not in query:
        query[key_param] = api_key
    request_url = urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query)))
    timeout = int(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", "10"))
    with urllib.request.urlopen(request_url, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def data_go_kr_service_key() -> str | None:
    return (
        os.getenv("SPATIAL_DATA_GO_KR_SERVICE_KEY")
        or os.getenv("COMPLAINT_BIGDATA_API_KEY")
        or os.getenv("COMPLAINT_BIGDATA_SERVICE_KEY")
        or os.getenv("POLICY_QNA_API_KEY")
    )


def sgis_access_token() -> str | None:
    token = clean(os.getenv("SGIS_ACCESS_TOKEN"))
    if token:
        return token
    consumer_key = clean(os.getenv("SGIS_CONSUMER_KEY"))
    consumer_secret = clean(os.getenv("SGIS_CONSUMER_SECRET"))
    if not consumer_key or not consumer_secret:
        return None
    auth_url = clean(os.getenv("SGIS_AUTH_URL")) or "https://sgisapi.kostat.go.kr/OpenAPI3/auth/authentication.json"
    parsed = urllib.parse.urlparse(auth_url)
    query = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
    query.setdefault("consumer_key", consumer_key)
    query.setdefault("consumer_secret", consumer_secret)
    request_url = urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query)))
    response = fetch_json(request_url, None)
    result = response.get("result") or {}
    return clean(result.get("accessToken") or result.get("access_token"))


def fetch_sgis_json(url: str) -> dict[str, Any]:
    token = sgis_access_token()
    if not token:
        raise RuntimeError("SGIS URL configured but SGIS_ACCESS_TOKEN or SGIS_CONSUMER_KEY/SGIS_CONSUMER_SECRET is missing")
    parsed = urllib.parse.urlparse(url)
    query = dict(urllib.parse.parse_qsl(parsed.query, keep_blank_values=True))
    query.setdefault("accessToken", token)
    request_url = urllib.parse.urlunparse(parsed._replace(query=urllib.parse.urlencode(query)))
    return fetch_json(request_url, None)


def feature_collection(path_or_url: str) -> dict[str, Any]:
    if path_or_url.startswith(("http://", "https://")):
        if "sgis" in path_or_url.lower():
            return fetch_sgis_json(path_or_url)
        return fetch_json(path_or_url, data_go_kr_service_key())
    return read_json(path_or_url)


def iter_coordinates(value: Any) -> Iterable[tuple[float, float]]:
    if isinstance(value, list):
        if len(value) >= 2 and isinstance(value[0], (int, float)) and isinstance(value[1], (int, float)):
            yield float(value[0]), float(value[1])
        else:
            for child in value:
                yield from iter_coordinates(child)


def bbox_from_geometry(geometry: dict[str, Any] | None) -> tuple[float | None, float | None, float | None, float | None]:
    if not geometry:
        return None, None, None, None
    points = list(iter_coordinates(geometry.get("coordinates")))
    if not points:
        return None, None, None, None
    longitudes = [point[0] for point in points]
    latitudes = [point[1] for point in points]
    return min(latitudes), max(latitudes), min(longitudes), max(longitudes)


def centroid_from_bbox(
    min_lat: float | None,
    max_lat: float | None,
    min_lon: float | None,
    max_lon: float | None,
) -> tuple[float | None, float | None]:
    if None in (min_lat, max_lat, min_lon, max_lon):
        return None, None
    return (float(min_lat) + float(max_lat)) / 2, (float(min_lon) + float(max_lon)) / 2


def raw_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True)


def configured_path(env_name: str) -> str | None:
    value = clean(os.getenv(env_name))
    if not value:
        return None
    return value
