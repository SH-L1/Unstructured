"""Fail-closed synchronization of official national-law provisions.

External calls finish before a database transaction begins. Only complete,
versioned national-law provisions can become VERIFIED_OFFICIAL knowledge.
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

from data.API.law_api_client import LAW_SITE_BASE_URL, format_detail_link, request_law_detail, search_laws


load_dotenv()

SOURCE_NAME = "Korean National Law Information Center"
SOURCE_TYPE = "OFFICIAL_LAW_API"
DEFAULT_QUERIES = (
    "민원 처리에 관한 법률",
    "행정절차법",
    "개인정보 보호법",
    "공공기관의 정보공개에 관한 법률",
    "지방자치법",
    "폐기물관리법",
    "도로법",
    "도로교통법",
    "주차장법",
    "소음진동관리법",
    "악취방지법",
    "대기환경보전법",
    "물환경보전법",
    "하수도법",
    "수도법",
    "하천법",
    "소하천정비법",
    "건축법",
    "건축물관리법",
    "주택법",
    "공동주택관리법",
    "국토의 계획 및 이용에 관한 법률",
    "도시공원 및 녹지 등에 관한 법률",
    "옥외광고물 등의 관리와 옥외광고산업 진흥에 관한 법률",
    "식품위생법",
    "공중위생관리법",
    "감염병의 예방 및 관리에 관한 법률",
    "재난 및 안전관리 기본법",
    "시설물의 안전 및 유지관리에 관한 특별법",
    "어린이놀이시설 안전관리법",
    "동물보호법",
    "가축분뇨의 관리 및 이용에 관한 법률",
    "가축전염병 예방법",
    "장애인복지법",
    "장애인ㆍ노인ㆍ임산부 등의 편의증진 보장에 관한 법률",
    "교통약자의 이동편의 증진법",
    "화학물질관리법",
    "위험물안전관리법",
    "지방행정제재ㆍ부과금의 징수 등에 관한 법률",
    "질서위반행위규제법",
    "행정대집행법",
)


@dataclass(frozen=True)
class Provision:
    key: str
    heading: str
    content: str


@dataclass(frozen=True)
class OfficialDocument:
    external_id: str
    title: str
    source_url: str
    effective_from: date
    content_hash: str
    content: str
    provisions: tuple[Provision, ...]


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


def parse_yyyymmdd(value: str) -> date:
    digits = re.sub(r"[^0-9]", "", value)
    if len(digits) != 8:
        raise ValueError(f"Official effective date is invalid: {value!r}")
    return datetime.strptime(digits, "%Y%m%d").date()


def parse_provisions(root: ET.Element) -> tuple[Provision, ...]:
    provisions: list[Provision] = []
    seen: set[str] = set()
    def direct_value(direct: dict[str, ET.Element], *names: str) -> ET.Element | None:
        for name in names:
            if name in direct:
                return direct[name]
        return None

    for element in root.iter():
        direct = {strip_namespace(child.tag): child for child in list(element)}
        number = direct_value(direct, "조문번호", "議곕Ц踰덊샇")
        content = direct_value(direct, "조문내용", "조내용", "議곕Ц?댁슜")
        if number is None or content is None:
            continue
        number_text = re.sub(r"^제|조$", "", normalized_text(number)).strip()
        branch = direct_value(direct, "조문가지번호", "議곕Ц媛吏踰덊샇")
        branch_text = normalized_text(branch) if branch is not None else ""
        branch_text = re.sub(r"^의", "", branch_text).strip()
        key = f"제{number_text}조" + (f"의{branch_text}" if branch_text and branch_text != "0" else "")
        if key in seen:
            continue
        content_text = normalized_text(element)
        if not content_text:
            continue
        heading_node = direct_value(direct, "조문제목", "조제목", "議곕Ц?쒕ぉ")
        heading = normalized_text(heading_node) if heading_node is not None else ""
        provisions.append(Provision(key=key[:200], heading=heading[:500], content=content_text))
        seen.add(key)
    if not provisions:
        raise ValueError("Official document did not contain preserved provision nodes")
    return tuple(provisions)


def build_document(item: dict[str, str], root: ET.Element) -> OfficialDocument:
    external_id = str(item.get("law_id") or "").strip()
    title = str(item.get("title") or "").strip()
    if not external_id or not title:
        raise ValueError("Official document is missing an external id or title")
    effective_from = parse_yyyymmdd(first_text(root, {"시행일자"}))
    provisions = parse_provisions(root)
    content = "\n\n".join(f"{provision.key} {provision.heading}\n{provision.content}" for provision in provisions)
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    detail_link = str(item.get("detail_link") or "").strip()
    source_url = format_detail_link(detail_link, mask_key=False) if detail_link else LAW_SITE_BASE_URL
    if not source_url.startswith("https://www.law.go.kr"):
        raise ValueError("Official document URL is outside the approved source")
    return OfficialDocument(
        external_id=external_id,
        title=title[:500],
        source_url=source_url[:500],
        effective_from=effective_from,
        content_hash=content_hash,
        content=content,
        provisions=provisions,
    )


def collect_documents() -> list[OfficialDocument]:
    queries = tuple(
        query.strip()
        for query in os.getenv("OFFICIAL_LAW_QUERIES", ",".join(DEFAULT_QUERIES)).split(",")
        if query.strip()
    )
    max_documents = int(os.getenv("OFFICIAL_SOURCE_MAX_DOCUMENTS", "30"))
    documents: dict[tuple[str, str], OfficialDocument] = {}
    for query in queries:
        items = search_laws(query, display=int(os.getenv("OFFICIAL_SOURCE_SEARCH_DISPLAY", "5")))
        for item in items:
            if len(documents) >= max_documents:
                break
            external_id = str(item.get("law_id") or "").strip()
            if not external_id:
                continue
            root = request_law_detail(external_id, "LAW_API")
            document = build_document(item, root)
            documents[(document.external_id, document.content_hash)] = document
    if not documents:
        raise RuntimeError("Official source synchronization produced no complete national-law documents")
    return list(documents.values())


def source_registry_id(cursor, interval_minutes: int) -> int:
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, created_at, updated_at
        ) values (%s, %s, %s, 'NATIONAL', 'SYNCING', %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            jurisdiction_code = excluded.jurisdiction_code,
            status = 'SYNCING',
            collection_interval_minutes = excluded.collection_interval_minutes,
            updated_at = excluded.updated_at
        returning id
        """,
        (SOURCE_NAME, SOURCE_TYPE, LAW_SITE_BASE_URL, interval_minutes, datetime.now(), datetime.now()),
    )
    return int(cursor.fetchone()[0])


def upsert_document(cursor, source_id: int, document: OfficialDocument) -> None:
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
            source_registry_id, external_id, title, jurisdiction_code,
            effective_from, effective_to, status, content_hash, created_at, updated_at
        ) values (%s, %s, %s, 'NATIONAL', %s, null, 'ACTIVE', %s, %s, %s)
        on conflict (source_registry_id, external_id, content_hash) do update set
            title = excluded.title,
            jurisdiction_code = excluded.jurisdiction_code,
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
    cursor.execute(
        """
        select id from knowledge_documents where title = %s order by id limit 1
        """,
        (document.title,),
    )
    existing = cursor.fetchone()
    values = {
        "document_type": "LAW",
        "title": document.title[:200],
        "source_name": SOURCE_NAME[:200],
        "source_url": document.source_url,
        "content": document.content,
        "keywords": document.title[:500],
        "legal_basis": document.title[:500],
        "purpose": "OFFICIAL_LAW",
        "verification_status": "VERIFIED_OFFICIAL",
        "jurisdiction_code": "NATIONAL",
        "effective_from": document.effective_from,
        "content_hash": document.content_hash,
        "source_version": f"{document.external_id}:{document.content_hash[:16]}",
        "source_registry_id": source_id,
        "updated_at": now,
    }
    if existing:
        values["id"] = int(existing[0])
        cursor.execute(
            """
            update knowledge_documents set
                document_type = %(document_type)s,
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
        ) values (%s, 'OFFICIAL_LAW', true, %s, %s)
        on conflict (knowledge_document_id, purpose) do update set
            legal_evidence_allowed = true,
            updated_at = excluded.updated_at
        """,
        (knowledge_id, now, now),
    )


def mark_sync_success(cursor, source_id: int, documents: list[OfficialDocument], interval_minutes: int) -> None:
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


def expire_documents_not_seen(cursor, source_id: int, documents: list[OfficialDocument]) -> None:
    now = datetime.now()
    current_versions = [
        f"{document.external_id}:{document.content_hash[:16]}" for document in documents
    ]
    current_hashes = [document.content_hash for document in documents]
    cursor.execute(
        """
        update knowledge_documents
        set verification_status = 'STALE',
            updated_at = %s
        where source_registry_id = %s
          and not (source_version = any(%s))
          and verification_status = 'VERIFIED_OFFICIAL'
        """,
        (now, source_id, current_versions),
    )
    cursor.execute(
        """
        update legal_document_versions
        set status = 'SUPERSEDED',
            effective_to = coalesce(effective_to, %s),
            updated_at = %s
        where source_registry_id = %s
          and not (content_hash = any(%s))
          and status = 'ACTIVE'
        """,
        (date.today(), now, source_id, current_hashes),
    )


def mark_sync_failure(reason: str) -> None:
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            now = datetime.now()
            cursor.execute(
                """
                insert into source_registry (
                    name, source_type, base_url, jurisdiction_code, status,
                    collection_interval_minutes, last_failure_at, last_failure_reason,
                    stale_after, created_at, updated_at
                ) values (%s, %s, %s, 'NATIONAL', 'FAILED', %s, %s, %s, %s, %s, %s)
                on conflict (name) do update set
                    status = 'FAILED',
                    last_failure_at = %s,
                    last_failure_reason = %s,
                    stale_after = %s,
                    updated_at = %s
                """,
                (
                    SOURCE_NAME,
                    SOURCE_TYPE,
                    LAW_SITE_BASE_URL,
                    int(os.getenv("OFFICIAL_SOURCE_INTERVAL_MINUTES", "1440")),
                    now,
                    reason[:2000],
                    now,
                    now,
                    now,
                    now,
                    reason[:2000],
                    now,
                    now,
                ),
            )
            expire_stale_documents(cursor)
        connection.commit()


def expire_stale_documents(cursor) -> None:
    cursor.execute(
        """
        update knowledge_documents k
        set verification_status = 'STALE',
            updated_at = %s
        from source_registry s
        where k.source_registry_id = s.id
          and (s.status <> 'ACTIVE' or s.stale_after is null or s.stale_after <= %s)
          and k.verification_status = 'VERIFIED_OFFICIAL'
        """,
        (datetime.now(), datetime.now()),
    )


def sync() -> int:
    if os.getenv("OFFICIAL_SOURCE_SYNC_ENABLED", "false").lower() != "true":
        raise RuntimeError("Set OFFICIAL_SOURCE_SYNC_ENABLED=true to run official source synchronization")
    interval_minutes = int(os.getenv("OFFICIAL_SOURCE_INTERVAL_MINUTES", "1440"))
    try:
        documents = collect_documents()
        with psycopg2.connect(**connection_kwargs()) as connection:
            with connection.cursor() as cursor:
                source_id = source_registry_id(cursor, interval_minutes)
                for document in documents:
                    upsert_document(cursor, source_id, document)
                expire_documents_not_seen(cursor, source_id, documents)
                mark_sync_success(cursor, source_id, documents, interval_minutes)
                expire_stale_documents(cursor)
            connection.commit()
        return len(documents)
    except Exception as exception:
        try:
            mark_sync_failure(str(exception))
        except Exception as failure_record_exception:
            exception.add_note(f"Could not persist source failure status: {failure_record_exception}")
        raise


if __name__ == "__main__":
    print(f"Synchronized {sync()} official national-law documents")
