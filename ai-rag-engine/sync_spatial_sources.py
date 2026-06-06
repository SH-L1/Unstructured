"""Load Asan GIS files/API responses into the spatial data mart.

The loader accepts CSV and GeoJSON files already downloaded from official
sources. API URLs can also be supplied for GeoJSON-compatible endpoints. The
tables are operational context only; they are never legal evidence.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import psycopg2

from spatial_data_common import (
    bbox_from_geometry,
    centroid_from_bbox,
    clean,
    configured_path,
    connection_kwargs,
    feature_collection,
    pick,
    raw_json,
    read_csv_records,
    sha256_text,
    to_float,
    utc_now,
)


FACILITY_SOURCES = {
    "SPATIAL_PARKING_LOTS_CSV": (1, "PARKING_LOT", "Asan parking lots"),
    "SPATIAL_PARKS_CSV": (1, "PARK", "Asan parks"),
    "SPATIAL_CCTV_CSV": (2, "CCTV", "Asan CCTVs"),
    "SPATIAL_STREETLIGHTS_CSV": (2, "STREETLIGHT", "Asan streetlights and security lights"),
    "SPATIAL_ROAD_SIGNS_CSV": (2, "ROAD_SIGN", "Asan road safety signs"),
    "SPATIAL_VMS_CSV": (2, "VMS", "Asan VMS and traffic facilities"),
    "SPATIAL_LIVESTOCK_FARMS_CSV": (3, "LIVESTOCK_FARM", "Asan livestock facilities"),
    "SPATIAL_PUBLIC_HOUSING_CSV": (3, "PUBLIC_HOUSING", "Asan public and apartment housing"),
    "SPATIAL_WATER_FACILITIES_CSV": (3, "WATER_FACILITY", "Asan water facilities"),
    "SPATIAL_SEWER_FACILITIES_CSV": (3, "SEWER_FACILITY", "Asan sewer and drainage facilities"),
    "SPATIAL_ROAD_MANAGEMENT_CSV": (3, "ROAD_MANAGEMENT_SEGMENT", "Asan road management ledger"),
}


@dataclass(frozen=True)
class SourceLoadResult:
    source_name: str
    inserted_rows: int


def normalize_address_record(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "road_address": pick(row, ("도로명주소", "road_address", "주소", "address")),
        "jibun_address": pick(row, ("지번주소", "jibun_address", "소재지지번주소")),
        "building_name": pick(row, ("건물명", "building_name", "시설명", "명칭")),
        "building_id": pick(row, ("건물관리번호", "building_id", "관리번호", "id")),
        "road_name": pick(row, ("도로명", "road_name")),
        "admin_code": pick(row, ("행정동코드", "admin_code")),
        "legal_dong_code": pick(row, ("법정동코드", "legal_dong_code")),
        "admin_name": pick(row, ("행정동명", "admin_name", "읍면동")),
        "legal_dong_name": pick(row, ("법정동명", "legal_dong_name")),
        "latitude": to_float(pick(row, ("위도", "latitude", "lat", "Y"))),
        "longitude": to_float(pick(row, ("경도", "longitude", "lon", "lng", "X"))),
        "raw_metadata": raw_json(row),
    }


def normalize_facility_record(row: dict[str, Any], facility_type: str) -> dict[str, Any]:
    latitude = to_float(pick(row, ("위도", "latitude", "lat", "Y")))
    longitude = to_float(pick(row, ("경도", "longitude", "lon", "lng", "X")))
    return {
        "facility_type": facility_type,
        "facility_name": pick(row, ("시설명", "명칭", "이름", "주차장명", "공원명", "단지명", "facility_name", "name")),
        "external_id": pick(row, ("관리번호", "시설ID", "id", "external_id")),
        "address": pick(row, ("소재지도로명주소", "도로명주소", "소재지지번주소", "주소", "address")),
        "admin_name": pick(row, ("읍면동", "행정동", "행정동명", "admin_name")),
        "manager": pick(row, ("관리기관명", "관리기관", "담당기관", "manager")),
        "phone": pick(row, ("전화번호", "연락처", "phone")),
        "operation_status": pick(row, ("운영상태", "상태", "operation_status")),
        "latitude": latitude,
        "longitude": longitude,
        "centroid_lat": latitude,
        "centroid_lon": longitude,
        "raw_metadata": raw_json(row),
    }


def normalize_parking_restriction_record(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "restriction_id": pick(row, ("관리번호", "단속구간ID", "id", "restriction_id")),
        "road_name": pick(row, ("도로명", "노선명", "road_name")),
        "detail_location": pick(row, ("상세위치", "구간", "위치", "detail_location")),
        "restriction_type": pick(row, ("단속유형", "제한유형", "restriction_type")),
        "weekday_time": pick(row, ("평일운영시간", "평일단속시간", "weekday_time")),
        "saturday_time": pick(row, ("토요일운영시간", "토요일단속시간", "saturday_time")),
        "holiday_time": pick(row, ("공휴일운영시간", "공휴일단속시간", "holiday_time")),
        "manager": pick(row, ("관리기관명", "관리기관", "manager")),
        "phone": pick(row, ("전화번호", "연락처", "phone")),
        "latitude": to_float(pick(row, ("위도", "latitude", "lat", "Y"))),
        "longitude": to_float(pick(row, ("경도", "longitude", "lon", "lng", "X"))),
        "raw_metadata": raw_json(row),
    }


def normalize_admin_boundary_feature(feature: dict[str, Any]) -> dict[str, Any]:
    properties = feature.get("properties") or {}
    geometry = feature.get("geometry")
    min_lat, max_lat, min_lon, max_lon = bbox_from_geometry(geometry)
    centroid_lat, centroid_lon = centroid_from_bbox(min_lat, max_lat, min_lon, max_lon)
    return {
        "boundary_type": pick(properties, ("boundary_type", "구분", "type")) or "EUPMYEONDONG",
        "boundary_code": pick(properties, ("boundary_code", "행정구역코드", "ADM_CD", "code")),
        "boundary_name": pick(properties, ("boundary_name", "행정구역명", "ADM_NM", "name")) or "UNKNOWN",
        "parent_code": pick(properties, ("parent_code", "상위코드", "parent")),
        "centroid_lat": centroid_lat,
        "centroid_lon": centroid_lon,
        "min_lat": min_lat,
        "max_lat": max_lat,
        "min_lon": min_lon,
        "max_lon": max_lon,
        "geometry_format": "GEOJSON",
        "geometry_payload": raw_json(geometry),
    }


def normalize_road_feature(feature: dict[str, Any]) -> dict[str, Any]:
    properties = feature.get("properties") or {}
    geometry = feature.get("geometry")
    min_lat, max_lat, min_lon, max_lon = bbox_from_geometry(geometry)
    centroid_lat, centroid_lon = centroid_from_bbox(min_lat, max_lat, min_lon, max_lon)
    return {
        "road_name": pick(properties, ("도로명", "노선명", "road_name", "name")),
        "road_code": pick(properties, ("도로구간ID", "도로명코드", "road_code", "id")),
        "road_type": pick(properties, ("도로종류", "road_type", "type")),
        "manager": pick(properties, ("관리기관명", "관리기관", "manager")),
        "detail_location": pick(properties, ("상세위치", "구간", "detail_location")),
        "centroid_lat": centroid_lat,
        "centroid_lon": centroid_lon,
        "min_lat": min_lat,
        "max_lat": max_lat,
        "min_lon": min_lon,
        "max_lon": max_lon,
        "geometry_format": "GEOJSON",
        "geometry_payload": raw_json(geometry),
        "raw_metadata": raw_json(properties),
    }


def upsert_source(cursor, source_name: str, source_type: str, phase: int, source_ref: str, content_hash: str) -> int:
    now = utc_now()
    cursor.execute(
        """
        insert into spatial_source_registry (
            source_name, source_type, phase, provider, source_url, jurisdiction_code,
            data_standard, collected_at, content_hash, status, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, 'ASAN', %s, %s, %s, 'ACTIVE', %s, %s)
        on conflict (source_name) do update set
            source_type = excluded.source_type,
            phase = excluded.phase,
            source_url = excluded.source_url,
            collected_at = excluded.collected_at,
            content_hash = excluded.content_hash,
            status = 'ACTIVE',
            updated_at = excluded.updated_at
        returning id
        """,
        (source_name, source_type, phase, "OFFICIAL_PUBLIC_SOURCE", source_ref[:500], "CSV_OR_GEOJSON", now, content_hash, now, now),
    )
    return int(cursor.fetchone()[0])


def clear_source_rows(cursor, source_id: int) -> None:
    for table in (
        "spatial_parking_restrictions",
        "spatial_facilities",
        "spatial_road_segments",
        "spatial_address_points",
        "spatial_admin_boundaries",
    ):
        cursor.execute(f"delete from {table} where source_id = %s", (source_id,))


def insert_admin_boundaries(cursor, source_ref: str) -> SourceLoadResult:
    data = feature_collection(source_ref)
    rows = [normalize_admin_boundary_feature(feature) for feature in data.get("features", [])]
    source_id = upsert_source(cursor, "Asan administrative boundaries", "ADMIN_BOUNDARY", 1, source_ref, sha256_text(raw_json(data)))
    clear_source_rows(cursor, source_id)
    now = utc_now()
    for row in rows:
        cursor.execute(
            """
            insert into spatial_admin_boundaries (
                source_id, boundary_type, boundary_code, boundary_name, parent_code,
                centroid_lat, centroid_lon, min_lat, max_lat, min_lon, max_lon,
                geometry_format, geometry_payload, created_at, updated_at
            ) values (
                %(source_id)s, %(boundary_type)s, %(boundary_code)s, %(boundary_name)s, %(parent_code)s,
                %(centroid_lat)s, %(centroid_lon)s, %(min_lat)s, %(max_lat)s, %(min_lon)s, %(max_lon)s,
                %(geometry_format)s, %(geometry_payload)s, %(now)s, %(now)s
            )
            """,
            {"source_id": source_id, "now": now, **row},
        )
    return SourceLoadResult("Asan administrative boundaries", len(rows))


def insert_address_points(cursor, source_ref: str) -> SourceLoadResult:
    raw_rows = read_csv_records(source_ref)
    rows = [normalize_address_record(row) for row in raw_rows]
    source_id = upsert_source(cursor, "Asan address points", "ADDRESS_POINT", 1, source_ref, sha256_text(raw_json(raw_rows)))
    clear_source_rows(cursor, source_id)
    now = utc_now()
    for row in rows:
        cursor.execute(
            """
            insert into spatial_address_points (
                source_id, road_address, jibun_address, building_name, building_id, road_name,
                admin_code, legal_dong_code, admin_name, legal_dong_name, latitude, longitude,
                raw_metadata, created_at, updated_at
            ) values (
                %(source_id)s, %(road_address)s, %(jibun_address)s, %(building_name)s, %(building_id)s, %(road_name)s,
                %(admin_code)s, %(legal_dong_code)s, %(admin_name)s, %(legal_dong_name)s, %(latitude)s, %(longitude)s,
                %(raw_metadata)s, %(now)s, %(now)s
            )
            """,
            {"source_id": source_id, "now": now, **row},
        )
    return SourceLoadResult("Asan address points", len(rows))


def insert_road_segments(cursor, source_ref: str) -> SourceLoadResult:
    data = feature_collection(source_ref)
    rows = [normalize_road_feature(feature) for feature in data.get("features", [])]
    source_id = upsert_source(cursor, "Asan road segments", "ROAD_SEGMENT", 1, source_ref, sha256_text(raw_json(data)))
    clear_source_rows(cursor, source_id)
    now = utc_now()
    for row in rows:
        cursor.execute(
            """
            insert into spatial_road_segments (
                source_id, road_name, road_code, road_type, manager, detail_location,
                centroid_lat, centroid_lon, min_lat, max_lat, min_lon, max_lon,
                geometry_format, geometry_payload, raw_metadata, created_at, updated_at
            ) values (
                %(source_id)s, %(road_name)s, %(road_code)s, %(road_type)s, %(manager)s, %(detail_location)s,
                %(centroid_lat)s, %(centroid_lon)s, %(min_lat)s, %(max_lat)s, %(min_lon)s, %(max_lon)s,
                %(geometry_format)s, %(geometry_payload)s, %(raw_metadata)s, %(now)s, %(now)s
            )
            """,
            {"source_id": source_id, "now": now, **row},
        )
    return SourceLoadResult("Asan road segments", len(rows))


def insert_facility_csv(cursor, env_name: str, phase: int, facility_type: str, source_name: str, source_ref: str) -> SourceLoadResult:
    raw_rows = read_csv_records(source_ref)
    rows = [normalize_facility_record(row, facility_type) for row in raw_rows]
    source_id = upsert_source(cursor, source_name, facility_type, phase, source_ref, sha256_text(raw_json(raw_rows)))
    clear_source_rows(cursor, source_id)
    now = utc_now()
    for row in rows:
        cursor.execute(
            """
            insert into spatial_facilities (
                source_id, facility_type, facility_name, external_id, address, admin_name, manager, phone,
                operation_status, latitude, longitude, centroid_lat, centroid_lon,
                raw_metadata, created_at, updated_at
            ) values (
                %(source_id)s, %(facility_type)s, %(facility_name)s, %(external_id)s, %(address)s, %(admin_name)s,
                %(manager)s, %(phone)s, %(operation_status)s, %(latitude)s, %(longitude)s,
                %(centroid_lat)s, %(centroid_lon)s, %(raw_metadata)s, %(now)s, %(now)s
            )
            """,
            {"source_id": source_id, "now": now, **row},
        )
    return SourceLoadResult(f"{source_name} ({env_name})", len(rows))


def insert_parking_restrictions(cursor, source_ref: str) -> SourceLoadResult:
    raw_rows = read_csv_records(source_ref)
    rows = [normalize_parking_restriction_record(row) for row in raw_rows]
    source_id = upsert_source(
        cursor,
        "Asan parking restrictions",
        "PARKING_RESTRICTION",
        1,
        source_ref,
        sha256_text(raw_json(raw_rows)),
    )
    clear_source_rows(cursor, source_id)
    now = utc_now()
    for row in rows:
        cursor.execute(
            """
            insert into spatial_parking_restrictions (
                source_id, restriction_id, road_name, detail_location, restriction_type,
                weekday_time, saturday_time, holiday_time, manager, phone, latitude, longitude,
                raw_metadata, created_at, updated_at
            ) values (
                %(source_id)s, %(restriction_id)s, %(road_name)s, %(detail_location)s, %(restriction_type)s,
                %(weekday_time)s, %(saturday_time)s, %(holiday_time)s, %(manager)s, %(phone)s,
                %(latitude)s, %(longitude)s, %(raw_metadata)s, %(now)s, %(now)s
            )
            """,
            {"source_id": source_id, "now": now, **row},
        )
    return SourceLoadResult("Asan parking restrictions", len(rows))


def configured_sources() -> dict[str, str]:
    env_names = (
        "SPATIAL_ADMIN_BOUNDARIES_GEOJSON",
        "SPATIAL_ADDRESS_POINTS_CSV",
        "SPATIAL_ROAD_SEGMENTS_GEOJSON",
        "SPATIAL_PARKING_RESTRICTIONS_CSV",
        *FACILITY_SOURCES.keys(),
    )
    sources: dict[str, str] = {}
    for env_name in env_names:
        path = configured_path(env_name)
        if not path:
            continue
        if path.startswith(("http://", "https://")) or Path(path).exists():
            sources[env_name] = path
        else:
            print(f"Skipping {env_name}: source does not exist yet ({path})")
    return sources


def sync() -> list[SourceLoadResult]:
    if os.getenv("SPATIAL_SYNC_ENABLED", "false").lower() != "true":
        raise RuntimeError("Set SPATIAL_SYNC_ENABLED=true before loading GIS data")
    sources = configured_sources()
    if not sources:
        raise RuntimeError("No GIS sources configured. Set one or more SPATIAL_*_CSV/GEOJSON variables in .env")

    results: list[SourceLoadResult] = []
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            if source_ref := sources.get("SPATIAL_ADMIN_BOUNDARIES_GEOJSON"):
                results.append(insert_admin_boundaries(cursor, source_ref))
            if source_ref := sources.get("SPATIAL_ADDRESS_POINTS_CSV"):
                results.append(insert_address_points(cursor, source_ref))
            if source_ref := sources.get("SPATIAL_ROAD_SEGMENTS_GEOJSON"):
                results.append(insert_road_segments(cursor, source_ref))
            if source_ref := sources.get("SPATIAL_PARKING_RESTRICTIONS_CSV"):
                results.append(insert_parking_restrictions(cursor, source_ref))
            for env_name, (phase, facility_type, source_name) in FACILITY_SOURCES.items():
                if source_ref := sources.get(env_name):
                    results.append(insert_facility_csv(cursor, env_name, phase, facility_type, source_name, source_ref))
        connection.commit()
    return results


if __name__ == "__main__":
    for result in sync():
        print(f"{result.source_name}: {result.inserted_rows}")
