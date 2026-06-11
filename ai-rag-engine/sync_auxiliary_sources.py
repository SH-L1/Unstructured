"""Lazy ingestion for non-legal auxiliary public API data.

This module stores public API results in the governed knowledge tables so RAG
and worker flows can use stable DB records instead of transient API responses.
The ingested documents are explicitly blocked from legal-evidence use.
"""

from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Callable

import psycopg2
from dotenv import load_dotenv

from data.API.complaint_bigdata_api_client import (
    DEFAULT_BASE_URL as COMPLAINT_BIGDATA_BASE_URL,
    get_base_url as get_complaint_bigdata_base_url,
    search_complaint_bigdata_documents,
)
from data.API.policy_qna_api_client import (
    DEFAULT_BASE_URL as POLICY_QNA_BASE_URL,
    get_base_url as get_policy_qna_base_url,
    search_policy_qna_documents,
)


load_dotenv()

DEFAULT_QUERIES = (
    "아산시 민원",
    "아산시 생활불편",
    "아산시 행정절차",
    "아산시 쓰레기 폐기물",
    "아산시 도로 교통 주차",
    "아산시 소음 악취 환경",
    "아산시 상하수도",
    "아산시 건축 주택 공동주택",
    "아산시 공원 녹지",
    "아산시 보건 위생",
    "아산시 안전 재난",
    "아산시 동물 축산",
    "아산시 광고물 노점",
    "아산시 장애인 노인 교통약자",
)


@dataclass(frozen=True)
class AuxiliarySource:
    name: str
    source_type: str
    base_url: str
    base_url_getter: Callable[[], str]
    purpose: str
    document_type: str
    fetcher: Callable[[str, int], list[dict[str, object]]]


@dataclass(frozen=True)
class AuxiliaryDocument:
    source: AuxiliarySource
    query: str
    title: str
    source_name: str
    source_url: str | None
    content: str
    content_hash: str


SOURCES = (
    AuxiliarySource(
        name="Korean Civil Complaint Big Data API",
        source_type="COMPLAINT_BIGDATA_API",
        base_url=COMPLAINT_BIGDATA_BASE_URL,
        base_url_getter=get_complaint_bigdata_base_url,
        purpose="HISTORICAL_CASE",
        document_type="CASE",
        fetcher=search_complaint_bigdata_documents,
    ),
    AuxiliarySource(
        name="Korean Civil Policy QnA API",
        source_type="POLICY_QNA_API",
        base_url=POLICY_QNA_BASE_URL,
        base_url_getter=get_policy_qna_base_url,
        purpose="PROCEDURE",
        document_type="FAQ",
        fetcher=search_policy_qna_documents,
    ),
)


def connection_kwargs() -> dict[str, object]:
    user = os.getenv("WORKER_DB_USER")
    password = os.getenv("WORKER_DB_PASSWORD")
    if not user or not password:
        raise RuntimeError("WORKER_DB_USER and WORKER_DB_PASSWORD are required")
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": user,
        "password": password,
        "connect_timeout": 10,
    }


def configured_queries() -> tuple[str, ...]:
    raw = os.getenv("AUXILIARY_SOURCE_QUERIES", ",".join(DEFAULT_QUERIES))
    queries = tuple(query.strip() for query in raw.split(",") if query.strip())
    if not queries:
        raise RuntimeError("AUXILIARY_SOURCE_QUERIES did not contain any usable query")
    return queries


def source_enabled(source: AuxiliarySource) -> bool:
    specific = os.getenv(f"{source.source_type}_SYNC_ENABLED")
    if specific is not None:
        return specific.lower() == "true"
    return os.getenv("AUXILIARY_SOURCE_SYNC_ENABLED", "false").lower() == "true"


def build_auxiliary_document(
    source: AuxiliarySource,
    query: str,
    document: dict[str, object],
) -> AuxiliaryDocument | None:
    title = str(document.get("title") or "").strip()
    content = str(document.get("content") or "").strip()
    if not title or not content:
        return None

    source_name = str(document.get("source_name") or source.name).strip()
    source_url = str(document.get("source_url") or source.base_url_getter()).strip() or None
    hash_input = "\n".join([source.source_type, query, title, source_name, content])
    content_hash = hashlib.sha256(hash_input.encode("utf-8")).hexdigest()
    return AuxiliaryDocument(
        source=source,
        query=query,
        title=title[:200],
        source_name=source_name[:200],
        source_url=source_url[:500] if source_url else None,
        content=content,
        content_hash=content_hash,
    )


def collect_documents() -> list[AuxiliaryDocument]:
    display = int(os.getenv("AUXILIARY_SOURCE_DISPLAY", "5"))
    collected: dict[tuple[str, str], AuxiliaryDocument] = {}
    for source in SOURCES:
        if not source_enabled(source):
            continue
        for query in configured_queries():
            for raw_document in source.fetcher(query, display):
                document = build_auxiliary_document(source, query, raw_document)
                if document:
                    collected[(source.source_type, document.content_hash)] = document
    return list(collected.values())


def source_registry_id(cursor, source: AuxiliarySource, interval_minutes: int) -> int:
    now = datetime.now()
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, created_at, updated_at
        ) values (%s, %s, %s, null, 'SYNCING', %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            status = 'SYNCING',
            collection_interval_minutes = excluded.collection_interval_minutes,
            updated_at = excluded.updated_at
        returning id
        """,
        (source.name, source.source_type, source.base_url_getter(), interval_minutes, now, now),
    )
    return int(cursor.fetchone()[0])


def _split_text_into_chunks(content: str, max_chunk_size: int = 1800) -> list[str]:
    """Split plain text content into chunks by paragraph boundaries."""
    paragraphs = [p.strip() for p in content.split("\n\n") if p.strip()]
    chunks: list[str] = []
    current = ""
    for paragraph in paragraphs:
        candidate = f"{current}\n\n{paragraph}".strip() if current else paragraph
        if len(candidate) <= max_chunk_size:
            current = candidate
        else:
            if current:
                chunks.append(current)
            current = paragraph
    if current:
        chunks.append(current)
    return chunks or [content]


def upsert_document(cursor, source_id: int, document: AuxiliaryDocument) -> None:
    now = datetime.now()
    values = {
        "document_type": document.source.document_type,
        "title": document.title,
        "source_name": document.source_name,
        "source_url": document.source_url,
        "content": document.content,
        "keywords": document.query[:500],
        "legal_basis": None,
        "purpose": document.source.purpose,
        "verification_status": "VERIFIED_OFFICIAL",
        "jurisdiction_code": None,
        "content_hash": document.content_hash,
        "source_version": f"{document.source.source_type}:{document.content_hash[:16]}",
        "source_registry_id": source_id,
        "updated_at": now,
    }
    cursor.execute(
        """
        select id from knowledge_documents
        where source_registry_id = %s and content_hash = %s
        order by id limit 1
        """,
        (source_id, document.content_hash),
    )
    existing = cursor.fetchone()
    if existing:
        values["id"] = int(existing[0])
        cursor.execute(
            """
            update knowledge_documents set
                document_type = %(document_type)s,
                title = %(title)s,
                source_name = %(source_name)s,
                source_url = %(source_url)s,
                content = %(content)s,
                keywords = %(keywords)s,
                legal_basis = %(legal_basis)s,
                purpose = %(purpose)s,
                verification_status = %(verification_status)s,
                jurisdiction_code = %(jurisdiction_code)s,
                effective_from = null,
                effective_to = null,
                content_hash = %(content_hash)s,
                source_version = %(source_version)s,
                source_registry_id = %(source_registry_id)s,
                updated_at = %(updated_at)s
            where id = %(id)s
            returning id
            """,
            values,
        )
    else:
        cursor.execute(
            """
            insert into knowledge_documents (
                document_type, title, source_name, source_url, content, keywords, legal_basis,
                purpose, verification_status, jurisdiction_code, effective_from, effective_to,
                content_hash, source_version, source_registry_id, created_at, updated_at
            ) values (
                %(document_type)s, %(title)s, %(source_name)s, %(source_url)s, %(content)s,
                %(keywords)s, %(legal_basis)s, %(purpose)s, %(verification_status)s,
                %(jurisdiction_code)s, null, null, %(content_hash)s, %(source_version)s,
                %(source_registry_id)s, %(updated_at)s, %(updated_at)s
            )
            returning id
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
        (knowledge_id, document.source.purpose, now, now),
    )
    # --- Create knowledge_document_chunks so RAG search can find this document ---
    cursor.execute(
        "UPDATE knowledge_document_chunks SET active = false, updated_at = %s WHERE knowledge_document_id = %s",
        (now, knowledge_id),
    )
    chunks = _split_text_into_chunks(document.content)
    for chunk_index, chunk_content in enumerate(chunks):
        cursor.execute(
            """
            INSERT INTO knowledge_document_chunks (
                knowledge_document_id, chunk_index, content, keywords, legal_basis,
                token_count, active, created_at, updated_at
            ) VALUES (%s, %s, %s, %s, %s, %s, true, %s, %s)
            ON CONFLICT (knowledge_document_id, chunk_index)
            DO UPDATE SET
                content = EXCLUDED.content,
                keywords = EXCLUDED.keywords,
                legal_basis = EXCLUDED.legal_basis,
                token_count = EXCLUDED.token_count,
                active = true,
                updated_at = EXCLUDED.updated_at
            """,
            (
                knowledge_id,
                chunk_index,
                chunk_content,
                document.query[:500],
                None,
                len(chunk_content.split()),
                now,
                now,
            ),
        )


def mark_sync_success(cursor, source: AuxiliarySource, source_id: int, documents: list[AuxiliaryDocument], interval_minutes: int) -> None:
    now = datetime.now()
    hashes = sorted(document.content_hash for document in documents if document.source == source)
    aggregate_hash = hashlib.sha256("".join(hashes).encode("ascii")).hexdigest() if hashes else None
    cursor.execute(
        """
        update source_registry
        set status = 'ACTIVE',
            last_verified_at = %s,
            last_successful_sync_at = %s,
            last_failure_reason = null,
            next_sync_at = %s,
            stale_after = %s,
            last_content_hash = %s,
            updated_at = %s
        where id = %s
        """,
        (
            now,
            now,
            now + timedelta(minutes=interval_minutes),
            now + timedelta(minutes=interval_minutes * 2),
            aggregate_hash,
            now,
            source_id,
        ),
    )


def sync() -> int:
    if os.getenv("AUXILIARY_SOURCE_SYNC_ENABLED", "false").lower() != "true" and not any(
        os.getenv(f"{source.source_type}_SYNC_ENABLED", "false").lower() == "true" for source in SOURCES
    ):
        raise RuntimeError("Set AUXILIARY_SOURCE_SYNC_ENABLED=true or a source-specific *_SYNC_ENABLED=true")

    interval_minutes = int(os.getenv("AUXILIARY_SOURCE_INTERVAL_MINUTES", "1440"))
    documents = collect_documents()
    documents_by_source = {source: [document for document in documents if document.source == source] for source in SOURCES}
    with psycopg2.connect(**connection_kwargs()) as connection:
        connection.set_client_encoding('UTF8')
        with connection.cursor() as cursor:
            for source, source_documents in documents_by_source.items():
                if not source_enabled(source):
                    continue
                source_id = source_registry_id(cursor, source, interval_minutes)
                for document in source_documents:
                    upsert_document(cursor, source_id, document)
                mark_sync_success(cursor, source, source_id, documents, interval_minutes)
        connection.commit()
    return len(documents)


if __name__ == "__main__":
    print(f"Synchronized {sync()} auxiliary public API documents")
