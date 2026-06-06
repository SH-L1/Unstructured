"""Ingest the downloaded Asan organization chart into routing tables.

The organization chart is operational routing/reference data only. It can help
rank department candidates, but it must not become legal evidence and it must
not make final routing decisions without human review.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import uuid
from dataclasses import dataclass
from datetime import UTC, date, datetime
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile

import psycopg2
from dotenv import load_dotenv


load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
SOURCE_TYPE = "ASAN_ORGANIZATION_FILE"
SOURCE_NAME = "Asan city organization chart"
PURPOSE = "ORGANIZATION_ROUTING"
JURISDICTION_CODE = "ASAN"
DOCX_NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}


@dataclass(frozen=True)
class OrganizationRecord:
    sequence: int
    unit_name: str
    position: str
    phone: str
    duty: str


TYPE_KEYWORDS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("ILLEGAL_DUMPING", ("폐기물", "쓰레기", "무단투기", "청소", "재활용", "음식물")),
    ("ROAD_DAMAGE", ("도로", "보도", "포트홀", "굴착", "포장", "가로등")),
    ("ILLEGAL_PARKING", ("주정차", "주차", "교통지도", "단속", "자동차")),
    ("TRAFFIC_SIGN", ("교통", "신호", "표지", "횡단보도", "차선")),
    ("NOISE", ("소음", "진동")),
    ("ENVIRONMENT", ("환경", "악취", "대기", "수질", "오염")),
    ("HAZARDOUS_MATERIAL", ("안전", "위험물", "재난", "화학")),
)


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


def now() -> datetime:
    return datetime.now(UTC).replace(tzinfo=None)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def compact_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def normalize_space(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def source_path() -> Path:
    configured = os.getenv("ASAN_ORGANIZATION_FILE", "data/organization/asan_city_organization.docx")
    path = Path(configured)
    resolved = path if path.is_absolute() else BASE_DIR / path
    if resolved.exists():
        return resolved
    if resolved.suffix.lower() != ".docx":
        docx_candidate = resolved.with_suffix(".docx")
        if docx_candidate.exists():
            return docx_candidate
    return resolved


def extract_docx_paragraphs(path: Path) -> list[str]:
    with ZipFile(path) as archive:
        root = ET.fromstring(archive.read("word/document.xml"))
    paragraphs: list[str] = []
    for paragraph in root.findall(".//w:p", DOCX_NS):
        text = "".join(node.text or "" for node in paragraph.findall(".//w:t", DOCX_NS))
        text = normalize_space(text)
        if text:
            paragraphs.append(text)
    return paragraphs


def parse_records(paragraphs: list[str]) -> list[OrganizationRecord]:
    records: list[OrganizationRecord] = []
    phone_pattern = re.compile(r"0\d{1,2}-\d{3,4}-\d{4}")
    index = 0
    while index + 5 < len(paragraphs):
        token = paragraphs[index]
        if token.isdigit() and phone_pattern.fullmatch(paragraphs[index + 4] or ""):
            records.append(
                OrganizationRecord(
                    sequence=int(token),
                    unit_name=paragraphs[index + 1],
                    position=paragraphs[index + 2],
                    phone=paragraphs[index + 4],
                    duty=paragraphs[index + 5],
                )
            )
            index += 6
            continue
        index += 1
    return records


def unit_code(unit_name: str) -> str:
    digest = sha256_text(unit_name)[:18].upper()
    return f"ASAN_ORG_{digest}"


def infer_complaint_types(record: OrganizationRecord) -> list[str]:
    text = f"{record.unit_name} {record.position} {record.duty}"
    matches = [complaint_type for complaint_type, keywords in TYPE_KEYWORDS if any(keyword in text for keyword in keywords)]
    return matches or ["GENERAL"]


def source_registry_id(cursor, path: Path) -> int:
    current = now()
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, last_verified_at, created_at, updated_at
        ) values (%s, %s, %s, %s, 'ACTIVE', 10080, %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            jurisdiction_code = excluded.jurisdiction_code,
            status = 'ACTIVE',
            last_verified_at = excluded.last_verified_at,
            updated_at = excluded.updated_at
        returning id
        """,
        (SOURCE_NAME, SOURCE_TYPE, str(path), JURISDICTION_CODE, current, current, current),
    )
    return int(cursor.fetchone()[0])


def start_run(cursor, source_id: int) -> str:
    run_id = str(uuid.uuid4())
    current = now()
    cursor.execute(
        """
        insert into data_mart_ingestion_runs (
            id, source_registry_id, source_type, source_name, purpose, status,
            started_at, record_count, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, 'RUNNING', %s, 0, %s, %s)
        """,
        (run_id, source_id, SOURCE_TYPE, SOURCE_NAME, PURPOSE, current, current, current),
    )
    return run_id


def finish_run(cursor, run_id: str, count: int, status: str = "COMPLETED", failure: str | None = None) -> None:
    current = now()
    cursor.execute(
        """
        update data_mart_ingestion_runs
        set status = %s, completed_at = %s, record_count = %s, failure_reason = %s, updated_at = %s
        where id = %s
        """,
        (status, current, count, failure, current, run_id),
    )


def sanitized_payload(path: Path, records: list[OrganizationRecord]) -> dict[str, object]:
    stat = path.stat()
    return {
        "relativePath": path.relative_to(BASE_DIR).as_posix(),
        "fileName": path.name,
        "suffix": path.suffix.lower(),
        "sizeBytes": stat.st_size,
        "modifiedAt": datetime.fromtimestamp(stat.st_mtime).isoformat(),
        "sourceType": SOURCE_TYPE,
        "purpose": PURPOSE,
        "recordCount": len(records),
        "records": [
            {
                "sequence": record.sequence,
                "unitName": record.unit_name,
                "position": record.position,
                "officePhone": record.phone,
                "duty": record.duty,
                "complaintTypes": infer_complaint_types(record),
            }
            for record in records
        ],
    }


def upsert_raw_record(cursor, run_id: str, source_id: int, path: Path, payload: dict[str, object]) -> str:
    current = now()
    raw_payload = compact_json(payload)
    raw_hash = sha256_text(raw_payload)
    cursor.execute(
        """
        insert into data_mart_raw_records (
            id, ingestion_run_id, source_registry_id, source_type, external_id, source_name,
            source_url, response_content_type, raw_payload, raw_payload_hash, fetched_at, created_at
        ) values (%s, %s, %s, %s, %s, %s, %s, 'application/json', %s, %s, %s, %s)
        on conflict (source_registry_id, external_id, raw_payload_hash) do update set
            ingestion_run_id = excluded.ingestion_run_id,
            fetched_at = excluded.fetched_at
        returning id
        """,
        (
            str(uuid.uuid4()),
            run_id,
            source_id,
            SOURCE_TYPE,
            path.relative_to(BASE_DIR).as_posix(),
            SOURCE_NAME,
            str(path),
            raw_payload,
            raw_hash,
            current,
            current,
        ),
    )
    return str(cursor.fetchone()[0])


def upsert_knowledge_and_normalized(cursor, source_id: int, raw_id: str, path: Path, records: list[OrganizationRecord]) -> int:
    current = now()
    content = "\n".join(
        f"{record.unit_name} / {record.position} / {record.phone} / {record.duty}"
        for record in records
    )
    content_hash = sha256_text("\n".join([SOURCE_TYPE, path.as_posix(), content]))
    source_version = f"{SOURCE_TYPE}:{content_hash[:16]}"
    cursor.execute(
        "select id from knowledge_documents where source_registry_id = %s and source_version = %s",
        (source_id, source_version),
    )
    existing = cursor.fetchone()
    values = {
        "document_type": "MANUAL",
        "title": "Asan organization routing reference",
        "source_name": SOURCE_NAME,
        "source_url": str(path),
        "content": content[:12000],
        "keywords": "아산시 조직도 담당업무 부서 팀 라우팅",
        "purpose": PURPOSE,
        "verification_status": "VERIFIED_INTERNAL",
        "jurisdiction_code": JURISDICTION_CODE,
        "content_hash": content_hash,
        "source_version": source_version,
        "source_registry_id": source_id,
        "now": current,
    }
    if existing:
        values["id"] = int(existing[0])
        cursor.execute(
            """
            update knowledge_documents set
                document_type = %(document_type)s, title = %(title)s, source_name = %(source_name)s,
                source_url = %(source_url)s, content = %(content)s, keywords = %(keywords)s,
                legal_basis = null, purpose = %(purpose)s, verification_status = %(verification_status)s,
                jurisdiction_code = %(jurisdiction_code)s, content_hash = %(content_hash)s,
                updated_at = %(now)s
            where id = %(id)s returning id
            """,
            values,
        )
    else:
        cursor.execute(
            """
            insert into knowledge_documents (
                document_type, title, source_name, source_url, content, keywords, legal_basis,
                purpose, verification_status, jurisdiction_code, content_hash, source_version,
                source_registry_id, created_at, updated_at
            ) values (
                %(document_type)s, %(title)s, %(source_name)s, %(source_url)s, %(content)s,
                %(keywords)s, null, %(purpose)s, %(verification_status)s,
                %(jurisdiction_code)s, %(content_hash)s, %(source_version)s,
                %(source_registry_id)s, %(now)s, %(now)s
            ) returning id
            """,
            values,
        )
    knowledge_id = int(cursor.fetchone()[0])
    cursor.execute(
        """
        insert into knowledge_purpose (
            knowledge_document_id, purpose, legal_evidence_allowed, created_at, updated_at
        ) values (%s, %s, false, %s, %s)
        on conflict (knowledge_document_id, purpose) do update set
            legal_evidence_allowed = false,
            updated_at = excluded.updated_at
        """,
        (knowledge_id, PURPOSE, current, current),
    )
    metadata = compact_json({"extractor": "docx-wordprocessingml", "recordCount": len(records), "sourceType": SOURCE_TYPE})
    cursor.execute(
        """
        insert into data_mart_normalized_records (
            id, raw_record_id, knowledge_document_id, record_type, title, content,
            metadata_json, purpose, verification_status, legal_evidence_allowed,
            jurisdiction_code, content_hash, created_at, updated_at
        ) values (%s, %s, %s, 'ORGANIZATION_CHART', %s, %s, %s, %s, 'VERIFIED_INTERNAL', false, %s, %s, %s, %s)
        on conflict (raw_record_id, content_hash) do update set
            knowledge_document_id = excluded.knowledge_document_id,
            metadata_json = excluded.metadata_json,
            updated_at = excluded.updated_at
        """,
        (
            str(uuid.uuid4()),
            raw_id,
            knowledge_id,
            values["title"],
            values["content"],
            metadata,
            PURPOSE,
            JURISDICTION_CODE,
            content_hash,
            current,
            current,
        ),
    )
    return knowledge_id


def upsert_organization_units_and_rules(cursor, records: list[OrganizationRecord]) -> tuple[int, int]:
    current = now()
    valid_from = date.today()
    cursor.execute(
        """
        delete from assignment_rules ar
        using organization_units ou
        where ar.organization_unit_id = ou.id
          and ou.code like 'ASAN_ORG_%'
          and ar.synthetic_demo = false
        """
    )
    unit_ids: dict[str, int] = {}
    for record in records:
        code = unit_code(record.unit_name)
        if code in unit_ids:
            continue
        cursor.execute(
            """
            insert into organization_units (
                code, name, jurisdiction_code, synthetic_demo, active,
                valid_from, valid_to, created_at, updated_at
            ) values (%s, %s, %s, false, true, %s, null, %s, %s)
            on conflict (code) do update set
                name = excluded.name,
                jurisdiction_code = excluded.jurisdiction_code,
                synthetic_demo = false,
                active = true,
                valid_to = null,
                updated_at = excluded.updated_at
            returning id
            """,
            (code, record.unit_name[:200], JURISDICTION_CODE, valid_from, current, current),
        )
        unit_ids[code] = int(cursor.fetchone()[0])
    rule_count = 0
    for record in records:
        organization_unit_id = unit_ids[unit_code(record.unit_name)]
        for complaint_type in infer_complaint_types(record):
            rule_text = (
                f"Asan organization chart routing candidate; unit={record.unit_name}; "
                f"position={record.position}; officePhone={record.phone}; duty={record.duty}; "
                "human confirmation required"
            )
            priority = 70 if complaint_type != "GENERAL" else 20
            cursor.execute(
                """
                insert into assignment_rules (
                    organization_unit_id, complaint_type, jurisdiction_code, rule_text,
                    priority, synthetic_demo, active, valid_from, valid_to, created_at, updated_at
                ) values (%s, %s, %s, %s, %s, false, true, %s, null, %s, %s)
                """,
                (organization_unit_id, complaint_type, JURISDICTION_CODE, rule_text, priority, valid_from, current, current),
            )
            rule_count += 1
    return len(unit_ids), rule_count


def sync() -> dict[str, int]:
    path = source_path()
    if not path.exists():
        raise FileNotFoundError(f"Asan organization file does not exist: {path}")
    paragraphs = extract_docx_paragraphs(path)
    records = parse_records(paragraphs)
    if not records:
        raise RuntimeError(f"No organization records could be parsed from {path}")
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            source_id = source_registry_id(cursor, path)
            run_id = start_run(cursor, source_id)
            try:
                payload = sanitized_payload(path, records)
                raw_id = upsert_raw_record(cursor, run_id, source_id, path, payload)
                upsert_knowledge_and_normalized(cursor, source_id, raw_id, path, records)
                unit_count, rule_count = upsert_organization_units_and_rules(cursor, records)
                finish_run(cursor, run_id, len(records))
            except Exception as exc:
                finish_run(cursor, run_id, 0, "FAILED", str(exc)[:2000])
                raise
        connection.commit()
    return {"records": len(records), "organizationUnits": unit_count, "assignmentRules": rule_count}


if __name__ == "__main__":
    print(json.dumps(sync(), ensure_ascii=False, indent=2))
