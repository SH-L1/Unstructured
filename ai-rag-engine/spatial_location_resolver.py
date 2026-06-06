"""Deterministic location candidate resolver backed by the spatial data mart."""

from __future__ import annotations

import re
import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Any

import psycopg2

from spatial_data_common import clean, connection_kwargs


@dataclass(frozen=True)
class LocationCandidate:
    candidate_type: str
    label: str
    address: str | None
    source_layer: str
    source_record_id: str
    latitude: float | None
    longitude: float | None
    confidence: str
    confidence_score: float
    evidence_summary: str


def normalize_location_text(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def extract_search_terms(text: str) -> list[str]:
    normalized = normalize_location_text(text)
    terms: list[str] = []
    asan_match = re.search(r"(?:아산시\s*)?([가-힣0-9A-Za-z]+(?:읍|면|동|로|길)\s*[0-9가-힣A-Za-z\-]*)", normalized)
    if asan_match:
        terms.append(asan_match.group(1).strip())
    for token in re.split(r"[^0-9A-Za-z가-힣]+", normalized):
        token = token.strip()
        if len(token) >= 2 and token not in {"아산", "아산시", "민원", "신고", "요청"}:
            terms.append(token)
    deduped: list[str] = []
    for term in terms:
        if term not in deduped:
            deduped.append(term)
    return deduped[:8]


def score_candidate(text: str, label: str, address: str | None) -> tuple[str, float]:
    haystack = f"{label} {address or ''}".lower()
    needle = normalize_location_text(text).lower()
    if needle and needle in haystack:
        return "HIGH", 0.95
    if label and label.lower() in needle:
        return "HIGH", 0.9
    terms = extract_search_terms(text)
    if not terms:
        return "LOW", 0.2
    matches = sum(1 for term in terms if term.lower() in haystack)
    ratio = matches / len(terms)
    if ratio >= 0.5:
        return "MEDIUM", 0.7
    if matches:
        return "LOW", 0.45
    return "LOW", 0.2


def like_pattern(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return f"%{escaped}%"


def find_admin_boundary(cursor, latitude: float | None, longitude: float | None) -> int | None:
    if latitude is None or longitude is None:
        return None
    cursor.execute(
        """
        select id
        from spatial_admin_boundaries
        where min_lat <= %s and max_lat >= %s
          and min_lon <= %s and max_lon >= %s
        order by
          case boundary_type when 'EUPMYEONDONG' then 1 when 'BEOPJEONGDONG' then 2 else 3 end,
          id
        limit 1
        """,
        (latitude, latitude, longitude, longitude),
    )
    row = cursor.fetchone()
    return int(row[0]) if row else None


def query_candidates(cursor, text: str, limit: int = 10) -> list[LocationCandidate]:
    terms = extract_search_terms(text)
    if not terms:
        return []
    patterns = [like_pattern(term) for term in terms]
    candidates: dict[tuple[str, str], LocationCandidate] = {}

    def add(row: dict[str, Any]) -> None:
        confidence, score = score_candidate(text, row["label"], row.get("address"))
        key = (row["source_layer"], str(row["source_record_id"]))
        candidate = LocationCandidate(
            candidate_type=row["candidate_type"],
            label=row["label"],
            address=row.get("address"),
            source_layer=row["source_layer"],
            source_record_id=str(row["source_record_id"]),
            latitude=row.get("latitude"),
            longitude=row.get("longitude"),
            confidence=confidence,
            confidence_score=score,
            evidence_summary=row["evidence_summary"],
        )
        previous = candidates.get(key)
        if previous is None or previous.confidence_score < candidate.confidence_score:
            candidates[key] = candidate

    for pattern in patterns:
        cursor.execute(
            """
            select 'ADDRESS' as candidate_type,
                   coalesce(building_name, road_address, jibun_address) as label,
                   coalesce(road_address, jibun_address) as address,
                   'spatial_address_points' as source_layer,
                   cast(id as varchar) as source_record_id,
                   latitude, longitude,
                   'Address point matched official road/jibun address data' as evidence_summary
            from spatial_address_points
            where lower(coalesce(road_address, '') || ' ' || coalesce(jibun_address, '') || ' ' || coalesce(building_name, ''))
                  like lower(%s) escape '\\'
            limit %s
            """,
            (pattern, limit),
        )
        for row in cursor.fetchall():
            add(row_to_dict(cursor, row))

        cursor.execute(
            """
            select facility_type as candidate_type,
                   coalesce(facility_name, address, external_id) as label,
                   address,
                   'spatial_facilities' as source_layer,
                   cast(id as varchar) as source_record_id,
                   coalesce(latitude, centroid_lat) as latitude,
                   coalesce(longitude, centroid_lon) as longitude,
                   'Facility matched official/local spatial facility data' as evidence_summary
            from spatial_facilities
            where lower(coalesce(facility_name, '') || ' ' || coalesce(address, '') || ' ' || coalesce(admin_name, ''))
                  like lower(%s) escape '\\'
            limit %s
            """,
            (pattern, limit),
        )
        for row in cursor.fetchall():
            add(row_to_dict(cursor, row))

        cursor.execute(
            """
            select 'PARKING_RESTRICTION' as candidate_type,
                   coalesce(road_name, detail_location, restriction_id) as label,
                   detail_location as address,
                   'spatial_parking_restrictions' as source_layer,
                   cast(id as varchar) as source_record_id,
                   latitude, longitude,
                   'Parking restriction matched official enforcement-zone data' as evidence_summary
            from spatial_parking_restrictions
            where lower(coalesce(road_name, '') || ' ' || coalesce(detail_location, '') || ' ' || coalesce(restriction_type, ''))
                  like lower(%s) escape '\\'
            limit %s
            """,
            (pattern, limit),
        )
        for row in cursor.fetchall():
            add(row_to_dict(cursor, row))

        cursor.execute(
            """
            select 'ROAD_SEGMENT' as candidate_type,
                   coalesce(road_name, road_code) as label,
                   detail_location as address,
                   'spatial_road_segments' as source_layer,
                   cast(id as varchar) as source_record_id,
                   centroid_lat as latitude,
                   centroid_lon as longitude,
                   'Road segment matched official/local road ledger geometry' as evidence_summary
            from spatial_road_segments
            where lower(coalesce(road_name, '') || ' ' || coalesce(detail_location, '') || ' ' || coalesce(road_code, ''))
                  like lower(%s) escape '\\'
            limit %s
            """,
            (pattern, limit),
        )
        for row in cursor.fetchall():
            add(row_to_dict(cursor, row))

    return sorted(candidates.values(), key=lambda item: item.confidence_score, reverse=True)[:limit]


def row_to_dict(cursor, row: tuple[Any, ...]) -> dict[str, Any]:
    return {description.name: value for description, value in zip(cursor.description, row)}


def insert_resolution_run(
    cursor,
    complaint_id: str | None,
    complaint_issue_id: str | None,
    input_text: str,
    candidates: list[LocationCandidate],
) -> uuid.UUID:
    now = datetime.utcnow()
    run_id = uuid.uuid4()
    if not candidates:
        status = "BLOCKED"
        blocker = "NEEDS_LOCATION"
    else:
        status = "CANDIDATES_READY"
        blocker = None
    cursor.execute(
        """
        insert into spatial_location_resolution_runs (
            id, complaint_id, complaint_issue_id, input_text, normalized_location_text,
            status, blocker, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (str(run_id), complaint_id, complaint_issue_id, input_text, normalize_location_text(input_text), status, blocker, now, now),
    )
    for candidate in candidates:
        admin_boundary_id = find_admin_boundary(cursor, candidate.latitude, candidate.longitude)
        cursor.execute(
            """
            insert into spatial_location_candidates (
                id, run_id, candidate_type, label, address, source_layer, source_record_id,
                admin_boundary_id, confidence, confidence_score, needs_human_confirmation,
                latitude, longitude, evidence_summary, created_at
            ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, true, %s, %s, %s, %s)
            """,
            (
                str(uuid.uuid4()),
                str(run_id),
                candidate.candidate_type,
                candidate.label,
                candidate.address,
                candidate.source_layer,
                candidate.source_record_id,
                admin_boundary_id,
                candidate.confidence,
                candidate.confidence_score,
                candidate.latitude,
                candidate.longitude,
                candidate.evidence_summary,
                now,
            ),
        )
    return run_id


def resolve_location_text(
    text: str,
    complaint_id: str | None = None,
    complaint_issue_id: str | None = None,
    limit: int = 10,
) -> uuid.UUID:
    if not clean(text):
        raise ValueError("Location resolver input text is required")
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            candidates = query_candidates(cursor, text, limit=limit)
            run_id = insert_resolution_run(cursor, complaint_id, complaint_issue_id, text, candidates)
        connection.commit()
    return run_id


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Resolve complaint text to GIS-backed location candidates")
    parser.add_argument("text")
    parser.add_argument("--complaint-id")
    parser.add_argument("--issue-id")
    args = parser.parse_args()
    print(resolve_location_text(args.text, args.complaint_id, args.issue_id))
