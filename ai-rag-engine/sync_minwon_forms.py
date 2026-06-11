"""Ingest Chungnam civil complaint form/procedure API records.

These records are procedure references only. They may mention legal basis text,
but they are not verified article-level legal evidence and therefore are always
stored with legal_evidence_allowed=false.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import urllib.parse
import urllib.request
import uuid
from datetime import datetime
from typing import Any

import psycopg2
from dotenv import load_dotenv


load_dotenv()

SOURCE_NAME = "Chungnam Civil Complaint Form Open API"
SOURCE_TYPE = "MINWON_FORM_API"
PURPOSE = "PROCEDURE"


def connection_kwargs() -> dict[str, object]:
    user = os.getenv("WORKER_DB_USER") or os.getenv("DB_USER")
    password = os.getenv("WORKER_DB_PASSWORD") or os.getenv("DB_PASSWORD")
    if not user or not password:
        raise RuntimeError("DB_USER/DB_PASSWORD or WORKER_DB_USER/WORKER_DB_PASSWORD are required")
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": user,
        "password": password,
        "connect_timeout": 10,
    }


def now() -> datetime:
    return datetime.utcnow()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def compact_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def normalize_text(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def fetch_page(page_no: int, rows: int) -> dict[str, Any]:
    base_url = os.getenv("MINWON_FORM_API_BASE_URL", "https://www.chungnam.go.kr/cnbbs/openApiMinwonForm.do")
    params = {"pageNo": page_no, "numOfRows": rows}
    request = urllib.request.Request(
        f"{base_url}?{urllib.parse.urlencode(params)}",
        headers={"User-Agent": "asan-complaint-minwon-form-ingestor/1.0"},
    )
    timeout = int(os.getenv("PUBLIC_API_TIMEOUT_SECONDS", "20"))
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if isinstance(payload, list) and payload:
        return payload[0]
    if isinstance(payload, dict):
        return payload
    raise RuntimeError("Minwon form API returned an unsupported response shape")


def collect_records() -> list[dict[str, Any]]:
    rows = int(os.getenv("MINWON_FORM_API_NUM_OF_ROWS", "100"))
    first = fetch_page(1, rows)
    total = int(first.get("totalCount") or 0)
    records = list(first.get("items") or [])
    page = 2
    while len(records) < total:
        data = fetch_page(page, rows)
        items = list(data.get("items") or [])
        if not items:
            break
        records.extend(items)
        page += 1
    return [record for record in records if isinstance(record, dict)]


def content_for(record: dict[str, Any]) -> str:
    fields = [
        ("민원명", record.get("title")),
        ("담당부서", record.get("deptNm")),
        ("처리기간", record.get("field06")),
        ("수수료", record.get("field07")),
        ("신청방법/처리절차", record.get("field09")),
        ("구비서류", record.get("field11")),
        ("참고 법령명", record.get("field12")),
        ("유의사항", record.get("field10")),
        ("등록일", record.get("cdate")),
    ]
    return "\n".join(f"{label}: {normalize_text(value)}" for label, value in fields if normalize_text(value))


def source_registry_id(cursor) -> int:
    current = now()
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, last_verified_at, created_at, updated_at
        ) values (%s, %s, %s, 'CHUNGNAM', 'ACTIVE', 1440, %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            jurisdiction_code = excluded.jurisdiction_code,
            status = 'ACTIVE',
            last_verified_at = excluded.last_verified_at,
            updated_at = excluded.updated_at
        returning id
        """,
        (SOURCE_NAME, SOURCE_TYPE, os.getenv("MINWON_FORM_API_BASE_URL"), current, current, current),
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


def finish_run(cursor, run_id: str, count: int) -> None:
    current = now()
    cursor.execute(
        """
        update data_mart_ingestion_runs
        set status = 'COMPLETED', completed_at = %s, record_count = %s, updated_at = %s
        where id = %s
        """,
        (current, count, current, run_id),
    )


def upsert_record(cursor, run_id: str, source_id: int, record: dict[str, Any]) -> None:
    current = now()
    external_id = normalize_text(record.get("rnum") or record.get("title") or sha256_text(compact_json(record))[:16])
    raw_payload = compact_json(record)
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
            external_id,
            SOURCE_NAME,
            os.getenv("MINWON_FORM_API_BASE_URL"),
            raw_payload,
            raw_hash,
            current,
            current,
        ),
    )
    raw_id = str(cursor.fetchone()[0])
    content = content_for(record)
    if not content:
        return
    content_hash = sha256_text("\n".join([SOURCE_TYPE, external_id, content]))
    title = normalize_text(record.get("title"))[:200] or f"민원사무서식 {external_id}"
    values = {
        "document_type": "FAQ",
        "title": title,
        "source_name": SOURCE_NAME,
        "source_url": os.getenv("MINWON_FORM_API_BASE_URL"),
        "content": content,
        "keywords": title[:500],
        "legal_basis": normalize_text(record.get("field12"))[:500] or None,
        "purpose": PURPOSE,
        "verification_status": "VERIFIED_INTERNAL",
        "jurisdiction_code": "CHUNGNAM",
        "content_hash": content_hash,
        "source_version": f"{SOURCE_TYPE}:{content_hash[:16]}",
        "source_registry_id": source_id,
        "now": current,
    }
    cursor.execute(
        "select id from knowledge_documents where source_registry_id = %s and source_version = %s",
        (source_id, values["source_version"]),
    )
    existing = cursor.fetchone()
    if existing:
        values["id"] = int(existing[0])
        cursor.execute(
            """
            update knowledge_documents set
                title = %(title)s, content = %(content)s, legal_basis = %(legal_basis)s,
                purpose = %(purpose)s, verification_status = %(verification_status)s,
                content_hash = %(content_hash)s, updated_at = %(now)s
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
                %(keywords)s, %(legal_basis)s, %(purpose)s, %(verification_status)s,
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
    cursor.execute(
        """
        insert into data_mart_normalized_records (
            id, raw_record_id, knowledge_document_id, record_type, title, content,
            metadata_json, purpose, verification_status, legal_evidence_allowed,
            jurisdiction_code, content_hash, created_at, updated_at
        ) values (%s, %s, %s, 'FAQ', %s, %s, %s, %s, 'VERIFIED_INTERNAL', false, 'CHUNGNAM', %s, %s, %s)
        on conflict (raw_record_id, content_hash) do update set
            knowledge_document_id = excluded.knowledge_document_id,
            updated_at = excluded.updated_at
        """,
        (
            str(uuid.uuid4()),
            raw_id,
            knowledge_id,
            title[:500],
            content,
            compact_json(record),
            PURPOSE,
            content_hash,
            current,
            current,
        ),
    )


def sync() -> int:
    records = collect_records()
    with psycopg2.connect(**connection_kwargs()) as connection:
        connection.set_client_encoding('UTF8')
        with connection.cursor() as cursor:
            source_id = source_registry_id(cursor)
            run_id = start_run(cursor, source_id)
            for record in records:
                upsert_record(cursor, run_id, source_id, record)
            finish_run(cursor, run_id, len(records))
        connection.commit()
    return len(records)


if __name__ == "__main__":
    print(f"minwon_forms: {sync()}")
