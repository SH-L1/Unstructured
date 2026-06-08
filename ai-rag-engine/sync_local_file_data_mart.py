"""Ingest currently downloaded local datasets into the governed data mart.

This loader is intentionally conservative:
- every acquired local file is registered as a raw data-mart record;
- only safely extractable text/metadata is normalized into knowledge records;
- local manuals, Saeol history, ordinance lists, and AIHub data are never legal
  evidence. Official legal claims must come from verified law API provisions.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shlex
import subprocess
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree as ET

try:
    import psycopg2
except ImportError:  # pragma: no cover - exercised in dependency-light unit tests
    psycopg2 = None
from dotenv import load_dotenv


load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
MAX_TEXT_CHARS = int(os.getenv("LOCAL_FILE_NORMALIZED_MAX_CHARS", "12000"))
MIN_HWP_MEANINGFUL_CHARS = int(os.getenv("LOCAL_FILE_HWP_MIN_MEANINGFUL_CHARS", "100"))
SUPPORTED_TEXT_SUFFIXES = {".txt", ".csv", ".json", ".md"}
ZIP_TEXT_SUFFIXES = {".hwpx", ".xlsx"}
ZIP_PAYLOAD_TEXT_SUFFIXES = {".json", ".jsonl", ".txt", ".csv", ".md", ".xml"}
MAX_ZIP_TEXT_ENTRIES = int(os.getenv("LOCAL_FILE_ZIP_TEXT_MAX_ENTRIES", "120"))
MAX_ZIP_MEMBER_BYTES = int(os.getenv("LOCAL_FILE_ZIP_MEMBER_MAX_BYTES", "1048576"))


@dataclass(frozen=True)
class LocalSource:
    source_type: str
    source_name: str
    root: Path
    purpose: str
    document_type: str
    jurisdiction_code: str | None = None


class LowQualityHwpText(RuntimeError):
    def __init__(self, meaningful_chars: int) -> None:
        super().__init__(f"HWP extracted text has only {meaningful_chars} meaningful chars")
        self.meaningful_chars = meaningful_chars


SOURCES = (
    LocalSource(
        "MINWON_MANUAL_FILES",
        "Asan civil complaint manuals 2018-2026",
        BASE_DIR / os.getenv("MINWON_MANUAL_DIR", "data/minwon_manuals"),
        "PROCEDURE",
        "MANUAL",
        "ASAN",
    ),
    LocalSource(
        "ASAN_ORDINANCE_LIST_FILE",
        "Asan current ordinance list file",
        BASE_DIR / "data/local_ordinances",
        "LOCAL_ORDINANCE_REFERENCE",
        "ORDINANCE",
        "ASAN",
    ),
    LocalSource(
        "SAEOL_PUBLIC_COMPLAINTS_FILE",
        "Asan Saeol public consultation complaints 2021+",
        BASE_DIR / "data/saeol",
        "HISTORICAL_CASE",
        "CASE",
        "ASAN",
    ),
    LocalSource(
        "AIHUB_DOCUMENT_VISUAL_FILES",
        "AIHub document visual dataset",
        BASE_DIR / os.getenv("AIHUB_DOCUMENT_VISUAL_DIR", "data/aihub/document_visual"),
        "EVALUATION_TRAINING",
        "SYNTHETIC",
        None,
    ),
    LocalSource(
        "AIHUB_PUBLIC_COMPLAINT_LLM_FILES",
        "AIHub public civil complaint LLM dataset",
        BASE_DIR / os.getenv("AIHUB_PUBLIC_COMPLAINT_LLM_DIR", "data/aihub/public_complaint_llm"),
        "STYLE_REFERENCE",
        "SYNTHETIC",
        None,
    ),
    LocalSource(
        "AIHUB_ADMINISTRATIVE_LAW_LLM_FILES",
        "AIHub administrative law LLM dataset",
        BASE_DIR / os.getenv("AIHUB_ADMINISTRATIVE_LAW_LLM_DIR", "data/aihub/administrative_law_llm"),
        "EVALUATION_TRAINING",
        "SYNTHETIC",
        None,
    ),
)


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


def utc_now() -> datetime:
    return datetime.utcnow()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def compact_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def read_text_file(path: Path) -> str:
    last_error: Exception | None = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            return path.read_text(encoding=encoding)
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError(f"Could not decode {path}") from last_error


def decode_text_bytes(data: bytes) -> str:
    last_error: Exception | None = None
    for encoding in ("utf-8-sig", "utf-8", "cp949", "euc-kr"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError as exc:
            last_error = exc
    raise RuntimeError("Could not decode zip member text") from last_error


def compact_json_text(value: object) -> str:
    if isinstance(value, dict):
        parts: list[str] = []
        for key, item in value.items():
            if isinstance(item, (dict, list)):
                nested = compact_json_text(item)
                if nested:
                    parts.append(f"{key}: {nested}")
            elif item is not None:
                parts.append(f"{key}: {item}")
        return " ".join(parts)
    if isinstance(value, list):
        return " ".join(compact_json_text(item) for item in value)
    return "" if value is None else str(value)


def redact_pii(text: str) -> tuple[str, list[dict[str, int | str]]]:
    patterns = {
        "resident_registration_number": r"\b\d{6}-?[1-4]\d{6}\b",
        "phone_number": r"\b(?:01[016789]-?\d{3,4}-?\d{4}|0\d{1,2}-?\d{3,4}-?\d{4})\b",
        "email": r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b",
    }
    redacted = text
    findings: list[dict[str, int | str]] = []
    for finding_type, pattern in patterns.items():
        matches = re.findall(pattern, redacted)
        if matches:
            findings.append({"type": finding_type, "count": len(matches)})
            redacted = re.sub(pattern, f"[REDACTED_{finding_type.upper()}]", redacted)
    return redacted, findings


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("\x00", " ")).strip()


def meaningful_text_length(text: str) -> int:
    stripped = re.sub(r"<표>|\s+", "", text)
    return len(stripped)


def xml_text_from_zip(path: Path) -> str:
    parts: list[str] = []
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            if not name.lower().endswith((".xml", ".rels")):
                continue
            try:
                data = archive.read(name)
                root = ET.fromstring(data)
                text = normalize_space(" ".join(value.strip() for value in root.itertext() if value.strip()))
                if text:
                    parts.append(text)
            except (ET.ParseError, KeyError, UnicodeDecodeError, zipfile.BadZipFile):
                continue
            if sum(len(part) for part in parts) >= MAX_TEXT_CHARS:
                break
    return "\n".join(parts)[:MAX_TEXT_CHARS]


def payload_text_from_zip(path: Path) -> tuple[str, dict[str, object]]:
    parts: list[str] = []
    scanned = 0
    skipped_large = 0
    suffix_counts: dict[str, int] = {}
    with zipfile.ZipFile(path) as archive:
        for info in sorted(archive.infolist(), key=lambda item: item.filename):
            if info.is_dir():
                continue
            suffix = Path(info.filename).suffix.lower()
            if suffix not in ZIP_PAYLOAD_TEXT_SUFFIXES:
                continue
            suffix_counts[suffix] = suffix_counts.get(suffix, 0) + 1
            if info.file_size > MAX_ZIP_MEMBER_BYTES:
                skipped_large += 1
                continue
            try:
                text = decode_text_bytes(archive.read(info))
                if suffix in {".json", ".jsonl"}:
                    json_parts = []
                    for line in text.splitlines() if suffix == ".jsonl" else [text]:
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            json_parts.append(compact_json_text(json.loads(line)))
                        except json.JSONDecodeError:
                            json_parts.append(line)
                    text = "\n".join(json_parts)
                elif suffix == ".xml":
                    root = ET.fromstring(text.encode("utf-8"))
                    text = " ".join(value.strip() for value in root.itertext() if value.strip())
            except (RuntimeError, UnicodeDecodeError, ET.ParseError, KeyError, zipfile.BadZipFile):
                continue
            text = normalize_space(text)
            if text:
                parts.append(f"[{info.filename}]\n{text}")
                scanned += 1
            if scanned >= MAX_ZIP_TEXT_ENTRIES or sum(len(part) for part in parts) >= MAX_TEXT_CHARS:
                break
    metadata = {
        "zipPayloadTextEntries": scanned,
        "zipPayloadSkippedLargeEntries": skipped_large,
        "zipPayloadSuffixCounts": suffix_counts,
    }
    return "\n\n".join(parts)[:MAX_TEXT_CHARS], metadata


def run_text_command(command_template: str, path: Path, label: str) -> str:
    command = [
        part.replace("{input}", str(path))
        for part in shlex.split(command_template, posix=False)
    ]
    if "{input}" not in command_template:
        command.append(str(path))
    result = subprocess.run(
        command,
        capture_output=True,
        timeout=int(os.getenv("WORKER_EXTERNAL_COMMAND_TIMEOUT_SECONDS", "60")),
        check=False,
    )
    stdout = result.stdout.decode("utf-8", errors="replace")
    stderr = result.stderr.decode("utf-8", errors="replace")
    if result.returncode != 0:
        reason = (stderr or stdout or "unknown failure").strip()
        raise RuntimeError(f"{label} failed with exit {result.returncode}: {reason[:500]}")
    return stdout


def zip_manifest(path: Path) -> dict[str, object]:
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        suffix_counts: dict[str, int] = {}
        for info in infos:
            suffix = Path(info.filename).suffix.lower() or "<none>"
            suffix_counts[suffix] = suffix_counts.get(suffix, 0) + 1
        return {
            "entryCount": len(infos),
            "totalCompressedBytes": sum(info.compress_size for info in infos),
            "totalUncompressedBytes": sum(info.file_size for info in infos),
            "suffixCounts": suffix_counts,
            "sampleEntries": [info.filename for info in infos[:20]],
        }


def file_metadata(path: Path, source: LocalSource) -> dict[str, object]:
    stat = path.stat()
    payload: dict[str, object] = {
        "relativePath": path.relative_to(BASE_DIR).as_posix(),
        "fileName": path.name,
        "suffix": path.suffix.lower(),
        "sizeBytes": stat.st_size,
        "modifiedAt": datetime.fromtimestamp(stat.st_mtime).isoformat(),
        "sourceType": source.source_type,
        "purpose": source.purpose,
    }
    if path.suffix.lower() in {".zip", ".hwpx", ".xlsx"}:
        try:
            payload["zipManifest"] = zip_manifest(path)
        except zipfile.BadZipFile as exc:
            payload["zipError"] = str(exc)
    return payload


def iter_files(source: LocalSource) -> Iterable[Path]:
    if not source.root.exists():
        return ()
    if source.root.is_file():
        return (source.root,)
    max_files = int(os.getenv(f"{source.source_type}_MAX_FILES", os.getenv("LOCAL_FILE_MAX_FILES_PER_SOURCE", "0")))
    files = (
        path for path in source.root.rglob("*")
        if path.is_file()
           and path.name != ".gitkeep"
           and "__pycache__" not in path.parts
    )
    if max_files <= 0:
        return files
    return (path for index, path in enumerate(files) if index < max_files)


def selected_source_types() -> set[str] | None:
    value = os.getenv("LOCAL_FILE_SOURCE_TYPES", "").strip()
    if not value:
        return None
    return {item.strip() for item in value.split(",") if item.strip()}


def selected_suffixes() -> set[str] | None:
    value = os.getenv("LOCAL_FILE_SUFFIXES", "").strip()
    if not value:
        return None
    return {item.strip().lower() for item in value.split(",") if item.strip()}


def extract_normalized_text(path: Path, source: LocalSource) -> tuple[str, dict[str, object]] | None:
    suffix = path.suffix.lower()
    metadata: dict[str, object] = {"extractor": "metadata-only"}
    if suffix in SUPPORTED_TEXT_SUFFIXES:
        text = read_text_file(path)
        metadata["extractor"] = "plain-text"
    elif suffix in ZIP_TEXT_SUFFIXES:
        text = xml_text_from_zip(path)
        metadata["extractor"] = "zip-xml-text"
    elif suffix == ".hwp":
        command = os.getenv("WORKER_HWP_TEXT_COMMAND", "").strip()
        if not command:
            return None
        text = run_text_command(command, path, "WORKER_HWP_TEXT_COMMAND")
        metadata["extractor"] = "worker-hwp-text-command"
        metadata["hwpMeaningfulChars"] = meaningful_text_length(text)
    elif suffix == ".zip":
        if source.source_type.startswith("AIHUB_"):
            text, zip_metadata = payload_text_from_zip(path)
            metadata.update(zip_metadata)
            metadata["extractor"] = "zip-payload-text"
            if not text:
                manifest = zip_manifest(path)
                text = json.dumps(manifest, ensure_ascii=False, indent=2)
                metadata["extractor"] = "zip-manifest"
        else:
            manifest = zip_manifest(path)
            text = json.dumps(manifest, ensure_ascii=False, indent=2)
            metadata["extractor"] = "zip-manifest"
    else:
        return None
    redacted, findings = redact_pii(text)
    clean = normalize_space(redacted)[:MAX_TEXT_CHARS]
    if not clean:
        return None
    metadata["piiFindings"] = findings
    if suffix == ".hwp":
        metadata["hwpMeaningfulChars"] = meaningful_text_length(clean)
        if metadata["hwpMeaningfulChars"] < MIN_HWP_MEANINGFUL_CHARS:
            metadata["skipReason"] = "LOW_QUALITY_HWP_TEXT"
            raise LowQualityHwpText(int(metadata["hwpMeaningfulChars"]))
    return clean, metadata


def source_registry_id(cursor, source: LocalSource) -> int:
    now = utc_now()
    cursor.execute(
        """
        insert into source_registry (
            name, source_type, base_url, jurisdiction_code, status,
            collection_interval_minutes, last_verified_at, created_at, updated_at
        ) values (%s, %s, %s, %s, 'ACTIVE', 1440, %s, %s, %s)
        on conflict (name) do update set
            source_type = excluded.source_type,
            base_url = excluded.base_url,
            jurisdiction_code = excluded.jurisdiction_code,
            status = 'ACTIVE',
            last_verified_at = excluded.last_verified_at,
            updated_at = excluded.updated_at
        returning id
        """,
        (source.source_name, source.source_type, str(source.root), source.jurisdiction_code, now, now, now),
    )
    return int(cursor.fetchone()[0])


def start_run(cursor, source_id: int, source: LocalSource) -> str:
    run_id = str(uuid.uuid4())
    now = utc_now()
    cursor.execute(
        """
        insert into data_mart_ingestion_runs (
            id, source_registry_id, source_type, source_name, purpose, status,
            started_at, record_count, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, 'RUNNING', %s, 0, %s, %s)
        """,
        (run_id, source_id, source.source_type, source.source_name, source.purpose, now, now, now),
    )
    return run_id


def finish_run(cursor, run_id: str, count: int, status: str = "COMPLETED", failure: str | None = None) -> None:
    now = utc_now()
    cursor.execute(
        """
        update data_mart_ingestion_runs
        set status = %s, completed_at = %s, record_count = %s, failure_reason = %s, updated_at = %s
        where id = %s
        """,
        (status, now, count, failure, now, run_id),
    )


def log_load_error(
    cursor,
    run_id: str,
    raw_id: str | None,
    source: LocalSource,
    stage: str,
    code: str,
    message: str,
    retryable: bool,
) -> None:
    cursor.execute(
        """
        insert into data_mart_load_errors (
            id, ingestion_run_id, raw_record_id, source_type, error_stage,
            error_code, error_message, retryable, created_at
        ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            str(uuid.uuid4()),
            run_id,
            raw_id,
            source.source_type,
            stage,
            code,
            message[:2000],
            retryable,
            utc_now(),
        ),
    )


def remove_low_quality_hwp_promotions(cursor, source_id: int) -> int:
    cursor.execute(
        """
        select kd.id
        from knowledge_documents kd
        where kd.source_registry_id = %s
          and lower(kd.source_url) like '%%.hwp'
          and lower(kd.source_url) not like '%%.hwpx'
          and length(regexp_replace(kd.content, '<표>|\\s+', '', 'g')) < %s
        """,
        (source_id, MIN_HWP_MEANINGFUL_CHARS),
    )
    ids = [int(row[0]) for row in cursor.fetchall()]
    if not ids:
        return 0
    cursor.execute("delete from data_mart_normalized_records where knowledge_document_id = any(%s)", (ids,))
    cursor.execute("delete from knowledge_purpose where knowledge_document_id = any(%s)", (ids,))
    cursor.execute("delete from knowledge_documents where id = any(%s)", (ids,))
    return len(ids)


def upsert_raw_record(cursor, run_id: str, source_id: int, source: LocalSource, path: Path, payload: dict[str, object]) -> str:
    record_id = str(uuid.uuid4())
    now = utc_now()
    raw_payload = compact_json(payload)
    raw_hash = sha256_text(raw_payload)
    external_id = path.relative_to(BASE_DIR).as_posix()
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
            record_id,
            run_id,
            source_id,
            source.source_type,
            external_id,
            source.source_name,
            str(path),
            raw_payload,
            raw_hash,
            now,
            now,
        ),
    )
    return str(cursor.fetchone()[0])


def upsert_knowledge(cursor, source_id: int, source: LocalSource, path: Path, content: str, metadata: dict[str, object]) -> int:
    now = utc_now()
    title = path.stem[:200]
    content_hash = sha256_text("\n".join([source.source_type, path.as_posix(), content]))
    values = {
        "document_type": source.document_type,
        "title": title,
        "source_name": source.source_name[:200],
        "source_url": str(path),
        "content": content,
        "keywords": path.name[:500],
        "legal_basis": None,
        "purpose": source.purpose,
        "verification_status": "VERIFIED_INTERNAL",
        "jurisdiction_code": source.jurisdiction_code,
        "content_hash": content_hash,
        "source_version": f"{source.source_type}:{content_hash[:16]}",
        "source_registry_id": source_id,
        "now": now,
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
                document_type = %(document_type)s, title = %(title)s, source_name = %(source_name)s,
                source_url = %(source_url)s, content = %(content)s, keywords = %(keywords)s,
                legal_basis = %(legal_basis)s, purpose = %(purpose)s,
                verification_status = %(verification_status)s, jurisdiction_code = %(jurisdiction_code)s,
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
        (knowledge_id, source.purpose, now, now),
    )
    metadata["knowledgeDocumentId"] = knowledge_id
    return knowledge_id


def upsert_normalized(
    cursor,
    raw_id: str,
    knowledge_id: int,
    source: LocalSource,
    path: Path,
    content: str,
    metadata: dict[str, object],
) -> None:
    normalized_id = str(uuid.uuid4())
    now = utc_now()
    content_hash = sha256_text("\n".join([source.source_type, path.as_posix(), content]))
    cursor.execute(
        """
        insert into data_mart_normalized_records (
            id, raw_record_id, knowledge_document_id, record_type, title, content,
            metadata_json, purpose, verification_status, legal_evidence_allowed,
            jurisdiction_code, content_hash, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, %s, %s, %s, 'VERIFIED_INTERNAL', false, %s, %s, %s, %s)
        on conflict (raw_record_id, content_hash) do update set
            knowledge_document_id = excluded.knowledge_document_id,
            metadata_json = excluded.metadata_json,
            updated_at = excluded.updated_at
        """,
        (
            normalized_id,
            raw_id,
            knowledge_id,
            source.document_type,
            path.stem[:500],
            content,
            compact_json(metadata),
            source.purpose,
            source.jurisdiction_code,
            content_hash,
            now,
            now,
        ),
    )


def sync() -> dict[str, int]:
    if psycopg2 is None:
        raise RuntimeError("Install psycopg2 before synchronizing local file data mart sources")
    counts: dict[str, int] = {}
    source_filter = selected_source_types()
    suffix_filter = selected_suffixes()
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            for source in SOURCES:
                if source_filter is not None and source.source_type not in source_filter:
                    continue
                source_id = source_registry_id(cursor, source)
                run_id = start_run(cursor, source_id, source)
                count = 0
                try:
                    if source.source_type == "MINWON_MANUAL_FILES":
                        removed = remove_low_quality_hwp_promotions(cursor, source_id)
                        if removed:
                            log_load_error(
                                cursor,
                                run_id,
                                None,
                                source,
                                "CLEANUP",
                                "REMOVED_LOW_QUALITY_HWP_PROMOTIONS",
                                f"Removed {removed} previously promoted low-quality HWP knowledge records",
                                False,
                            )
                    for path in iter_files(source):
                        if suffix_filter is not None and path.suffix.lower() not in suffix_filter:
                            continue
                        payload = file_metadata(path, source)
                        raw_id = upsert_raw_record(cursor, run_id, source_id, source, path, payload)
                        try:
                            extracted = extract_normalized_text(path, source)
                        except LowQualityHwpText as exc:
                            log_load_error(
                                cursor,
                                run_id,
                                raw_id,
                                source,
                                "NORMALIZE",
                                "LOW_QUALITY_HWP_TEXT",
                                f"Skipped searchable promotion; meaningfulChars={exc.meaningful_chars}, minimum={MIN_HWP_MEANINGFUL_CHARS}",
                                False,
                            )
                            extracted = None
                        if extracted:
                            content, metadata = extracted
                            metadata = {**payload, **metadata}
                            knowledge_id = upsert_knowledge(cursor, source_id, source, path, content, metadata)
                            upsert_normalized(cursor, raw_id, knowledge_id, source, path, content, metadata)
                        count += 1
                    finish_run(cursor, run_id, count)
                    connection.commit()
                except Exception as exc:
                    connection.rollback()
                    source_id = source_registry_id(cursor, source)
                    run_id = start_run(cursor, source_id, source)
                    finish_run(cursor, run_id, count, "FAILED", str(exc)[:2000])
                    connection.commit()
                    raise
                counts[source.source_type] = count
    return counts


if __name__ == "__main__":
    for source_type, count in sync().items():
        print(f"{source_type}: {count}")
