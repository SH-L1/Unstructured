"""Synchronize Asan local ordinances from the National Law API.

Local ordinances are jurisdiction-scoped official documents. They are stored
with provision structure, effective dates, source URL, and content hashes, but
legal-evidence use stays disabled by default until a local-law verification
policy is explicitly approved.
"""

from __future__ import annotations

import hashlib
import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import date, datetime, timedelta

import psycopg2
from dotenv import load_dotenv

from data.API.law_api_client import LAW_SITE_BASE_URL, format_detail_link, request_law_detail, search_ordinances
from sync_official_sources import Provision, connection_kwargs, parse_provisions


load_dotenv()

SOURCE_NAME = "Asan Local Ordinances - National Law Information Center"
SOURCE_TYPE = "LOCAL_ORDINANCE_API"
DEFAULT_JURISDICTION_CODE = "ASAN"
DEFAULT_QUERIES = (
    "아산시 민원",
    "아산시 행정기구",
    "아산시 사무위임",
    "아산시 폐기물",
    "아산시 도로",
    "아산시 주차장",
    "아산시 교통안전",
    "아산시 소음",
    "아산시 악취",
    "아산시 수도",
    "아산시 하수도",
    "아산시 건축",
    "아산시 공동주택",
    "아산시 도시공원",
    "아산시 옥외광고물",
    "아산시 식품위생",
    "아산시 공중위생",
    "아산시 감염병",
    "아산시 재난",
    "아산시 안전관리",
    "아산시 어린이놀이시설",
    "아산시 동물보호",
    "아산시 가축분뇨",
    "아산시 장애인",
    "아산시 교통약자",
)


@dataclass(frozen=True)
class LocalOrdinanceDocument:
    external_id: str
    title: str
    source_url: str
    jurisdiction_code: str
    promulgated_at: date | None
    effective_from: date
    content_hash: str
    content: str
    provisions: tuple[Provision, ...]


def strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def normalized_text(element: ET.Element) -> str:
    return re.sub(r"\s+", " ", " ".join(value.strip() for value in element.itertext() if value.strip())).strip()


def first_text(root: ET.Element, names: set[str]) -> str:
    for element in root.iter():
        if strip_namespace(element.tag) in names:
            value = normalized_text(element)
            if value:
                return value
    return ""


def parse_yyyymmdd(value: str) -> date | None:
    digits = re.sub(r"[^0-9]", "", value or "")
    if not digits:
        return None
    if len(digits) != 8:
        raise ValueError(f"Local ordinance date is invalid: {value!r}")
    return datetime.strptime(digits, "%Y%m%d").date()


def assert_asan_scope(item: dict[str, str], root: ET.Element) -> None:
    scope_text = " ".join(
        value
        for value in (
            item.get("title", ""),
            item.get("source_name", ""),
            first_text(root, {"자치단체명", "소관부처명", "소관부처"}),
        )
        if value
    )
    if "아산" not in scope_text:
        raise ValueError(f"Local ordinance is outside Asan jurisdiction: {scope_text!r}")


def build_document(
    item: dict[str, str],
    root: ET.Element,
    jurisdiction_code: str = DEFAULT_JURISDICTION_CODE,
) -> LocalOrdinanceDocument:
    external_id = str(item.get("law_id") or "").strip()
    title = str(item.get("title") or "").strip()
    if not external_id or not title:
        raise ValueError("Local ordinance is missing an external id or title")
    assert_asan_scope(item, root)

    effective_from = parse_yyyymmdd(first_text(root, {"시행일자", "시행일"}))
    promulgated_at = parse_yyyymmdd(first_text(root, {"공포일자", "공포일"}))
    if effective_from is None:
        effective_from = promulgated_at
    if effective_from is None:
        raise ValueError(f"Local ordinance does not include an effective or promulgation date: {title}")

    provisions = parse_provisions(root)
    content = "\n\n".join(f"{provision.key} {provision.heading}\n{provision.content}" for provision in provisions)
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    detail_link = str(item.get("detail_link") or "").strip()
    source_url = format_detail_link(detail_link, mask_key=False) if detail_link else LAW_SITE_BASE_URL
    if not source_url.startswith("https://www.law.go.kr"):
        raise ValueError("Local ordinance URL is outside the approved source")
    return LocalOrdinanceDocument(
        external_id=external_id,
        title=title[:500],
        source_url=source_url[:500],
        jurisdiction_code=jurisdiction_code,
        promulgated_at=promulgated_at,
        effective_from=effective_from,
        content_hash=content_hash,
        content=content,
        provisions=provisions,
    )


def collect_documents() -> list[LocalOrdinanceDocument]:
    queries = tuple(
        query.strip()
        for query in os.getenv("ASAN_ORDINANCE_SEARCH_QUERIES", ",".join(DEFAULT_QUERIES)).split(",")
        if query.strip()
    )
    max_documents = int(os.getenv("ASAN_ORDINANCE_MAX_DOCUMENTS", "300"))
    display = int(os.getenv("ASAN_ORDINANCE_SEARCH_DISPLAY", "10"))
    jurisdiction_code = os.getenv("ASAN_ORDINANCE_JURISDICTION_CODE", DEFAULT_JURISDICTION_CODE).strip()
    documents: dict[tuple[str, str], LocalOrdinanceDocument] = {}
    for query in queries:
        for item in search_ordinances(query, display=display):
            if len(documents) >= max_documents:
                break
            external_id = str(item.get("law_id") or "").strip()
            if not external_id:
                continue
            root = request_law_detail(external_id, "ORDINANCE_API")
            document = build_document(item, root, jurisdiction_code)
            documents[(document.external_id, document.content_hash)] = document
    if not documents:
        raise RuntimeError("Asan ordinance synchronization produced no complete local-law documents")
    return list(documents.values())


def source_registry_id(cursor, interval_minutes: int, jurisdiction_code: str) -> int:
    now = datetime.now()
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, created_at, updated_at
        ) values (%s, %s, %s, %s, 'SYNCING', %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            jurisdiction_code = excluded.jurisdiction_code,
            status = 'SYNCING',
            collection_interval_minutes = excluded.collection_interval_minutes,
            updated_at = excluded.updated_at
        returning id
        """,
        (SOURCE_NAME, SOURCE_TYPE, LAW_SITE_BASE_URL, jurisdiction_code, interval_minutes, now, now),
    )
    return int(cursor.fetchone()[0])


def upsert_document(cursor, source_id: int, document: LocalOrdinanceDocument) -> None:
    now = datetime.now()
    cursor.execute(
        """
        update legal_document_versions
        set status = 'SUPERSEDED',
            effective_to = case
                when effective_from < %s then %s
                else effective_to
            end,
            updated_at = %s
        where source_registry_id = %s
          and external_id = %s
          and content_hash <> %s
          and status = 'ACTIVE'
        """,
        (
            document.effective_from,
            document.effective_from - timedelta(days=1),
            now,
            source_id,
            document.external_id,
            document.content_hash,
        ),
    )
    cursor.execute(
        """
        insert into legal_document_versions (
            source_registry_id, external_id, title, jurisdiction_code, promulgated_at,
            effective_from, effective_to, status, content_hash, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, %s, null, 'ACTIVE', %s, %s, %s)
        on conflict (source_registry_id, external_id, content_hash) do update set
            title = excluded.title,
            jurisdiction_code = excluded.jurisdiction_code,
            promulgated_at = excluded.promulgated_at,
            effective_from = excluded.effective_from,
            effective_to = null,
            status = 'ACTIVE',
            updated_at = excluded.updated_at
        returning id
        """,
        (
            source_id,
            document.external_id,
            document.title,
            document.jurisdiction_code,
            document.promulgated_at,
            document.effective_from,
            document.content_hash,
            now,
            now,
        ),
    )
    version_id = int(cursor.fetchone()[0])
    for provision in document.provisions:
        cursor.execute(
            """
            insert into legal_provisions (
                legal_document_version_id, provision_key, heading, content, created_at, updated_at
            ) values (%s, %s, %s, %s, %s, %s)
            on conflict (legal_document_version_id, provision_key) do update set
                heading = excluded.heading,
                content = excluded.content,
                updated_at = excluded.updated_at
            """,
            (version_id, provision.key, provision.heading, provision.content, now, now),
        )

    values = {
        "document_type": "ORDINANCE",
        "title": document.title[:200],
        "source_name": SOURCE_NAME[:200],
        "source_url": document.source_url,
        "content": document.content,
        "keywords": document.title[:500],
        "legal_basis": document.title[:500],
        "purpose": "OFFICIAL_LAW",
        "verification_status": "VERIFIED_INTERNAL",
        "jurisdiction_code": document.jurisdiction_code,
        "effective_from": document.effective_from,
        "content_hash": document.content_hash,
        "source_version": f"{document.external_id}:{document.content_hash[:16]}",
        "source_registry_id": source_id,
        "updated_at": now,
    }
    cursor.execute(
        "select id from knowledge_documents where source_registry_id = %s and source_version = %s order by id limit 1",
        (source_id, values["source_version"]),
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
                effective_from = %(effective_from)s,
                effective_to = null,
                content_hash = %(content_hash)s,
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
                %(jurisdiction_code)s, %(effective_from)s, null, %(content_hash)s,
                %(source_version)s, %(source_registry_id)s, %(updated_at)s, %(updated_at)s
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
        ) values (%s, 'OFFICIAL_LAW', false, %s, %s)
        on conflict (knowledge_document_id, purpose) do update set
            legal_evidence_allowed = false,
            updated_at = excluded.updated_at
        """,
        (knowledge_id, now, now),
    )


def mark_sync_success(cursor, source_id: int, documents: list[LocalOrdinanceDocument], interval_minutes: int) -> None:
    now = datetime.now()
    aggregate_hash = hashlib.sha256(
        "".join(sorted(document.content_hash for document in documents)).encode("ascii")
    ).hexdigest()
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
    if os.getenv("ASAN_ORDINANCE_SYNC_ENABLED", "false").lower() != "true":
        raise RuntimeError("Set ASAN_ORDINANCE_SYNC_ENABLED=true to run Asan ordinance synchronization")
    interval_minutes = int(os.getenv("ASAN_ORDINANCE_INTERVAL_MINUTES", "1440"))
    jurisdiction_code = os.getenv("ASAN_ORDINANCE_JURISDICTION_CODE", DEFAULT_JURISDICTION_CODE).strip()
    documents = collect_documents()
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            source_id = source_registry_id(cursor, interval_minutes, jurisdiction_code)
            for document in documents:
                upsert_document(cursor, source_id, document)
            mark_sync_success(cursor, source_id, documents, interval_minutes)
        connection.commit()
    return len(documents)


if __name__ == "__main__":
    print(f"Synchronized {sync()} Asan local ordinance documents")
