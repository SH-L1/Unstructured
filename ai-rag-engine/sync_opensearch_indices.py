"""Synchronize governed knowledge into purpose-specific OpenSearch indices.

The database remains authoritative. OpenSearch is a derived discovery index,
and only governed, non-stale documents are exported. Official legal documents
must pass deterministic source metadata and content-hash checks before export.
"""

from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass
from datetime import date

import psycopg2
from dotenv import load_dotenv


load_dotenv()

ALLOWED_PURPOSES = {
    "OFFICIAL_LAW",
    "PROCEDURE",
    "LOCAL_ORDINANCE_REFERENCE",
    "HISTORICAL_CASE",
    "STYLE_REFERENCE",
    "STYLE",
    "EVALUATION_TRAINING",
}
ALLOWED_STATUSES = {"VERIFIED_OFFICIAL", "VERIFIED_INTERNAL"}


@dataclass(frozen=True)
class IndexedDocument:
    document_id: int
    index_id: str
    purpose: str
    source: dict[str, object]


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


def purpose_index_name(purpose: str) -> str:
    normalized = purpose.strip().upper()
    if normalized not in ALLOWED_PURPOSES:
        raise ValueError(f"Knowledge purpose cannot be indexed: {purpose}")
    prefix = os.getenv("OPENSEARCH_INDEX_PREFIX", "civil-complaint-knowledge").strip()
    if not prefix:
        raise RuntimeError("OPENSEARCH_INDEX_PREFIX is required")
    return f"{prefix}-{normalized.lower().replace('_', '-')}"


def source_for_row(row: tuple[object, ...], today: date | None = None) -> IndexedDocument:
    effective_on = today or date.today()
    (
        document_id,
        title,
        source_name,
        source_url,
        content,
        keywords,
        legal_basis,
        purpose,
        status,
        jurisdiction_code,
        effective_from,
        effective_to,
        content_hash,
        source_version,
    ) = row[:14]
    provision_id, provision_key, provision_heading, provision_content = (
        row[14:18] if len(row) >= 18 else (None, None, None, None)
    )
    purpose = str(purpose)
    status = str(status)
    content = str(content)
    if purpose not in ALLOWED_PURPOSES or status not in ALLOWED_STATUSES:
        raise ValueError("Only governed, non-stale knowledge may be indexed")
    if effective_from and effective_from > effective_on:
        raise ValueError("Future knowledge may not be indexed as current")
    if effective_to and effective_to < effective_on:
        raise ValueError("Expired knowledge may not be indexed")
    actual_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    if not content_hash or actual_hash != content_hash:
        raise ValueError("Knowledge content hash does not match the governed source")
    if purpose == "OFFICIAL_LAW":
        if status != "VERIFIED_OFFICIAL" or jurisdiction_code != "NATIONAL":
            raise ValueError("Official law must be verified and national")
        if not source_url or not str(source_url).startswith("https://www.law.go.kr"):
            raise ValueError("Official law must use an approved national-law URL")
        if not source_version or not legal_basis:
            raise ValueError("Official law requires source version and legal basis")
        if not provision_id or not provision_key or not provision_content:
            raise ValueError("Official law requires a preserved provision for indexing")
    indexed_content = str(provision_content) if provision_content else content
    embedding_text = "\n".join(
        value
        for value in (
            str(title),
            str(legal_basis or ""),
            str(provision_key or ""),
            str(provision_heading or ""),
            indexed_content,
        )
        if value
    )
    return IndexedDocument(
        document_id=int(document_id),
        index_id=f"{document_id}-{provision_id}" if provision_id else str(document_id),
        purpose=purpose,
        source={
            "documentId": int(document_id),
            "title": str(title),
            "sourceName": str(source_name),
            "sourceUrl": source_url,
            "provisionKey": provision_key,
            "heading": provision_heading,
            "content": indexed_content,
            "keywords": str(keywords),
            "legalBasis": legal_basis,
            "purpose": purpose,
            "verificationStatus": status,
            "jurisdictionCode": jurisdiction_code,
            "effectiveFrom": effective_from.isoformat() if effective_from else None,
            "effectiveTo": effective_to.isoformat() if effective_to else None,
            "contentHash": str(content_hash),
            "provisionContentHash": (
                hashlib.sha256(indexed_content.encode("utf-8")).hexdigest()
                if provision_id
                else None
            ),
            "sourceVersion": source_version,
            "embeddingText": embedding_text,
        },
    )


def load_documents() -> list[IndexedDocument]:
    with psycopg2.connect(**connection_kwargs()) as connection:
        connection.autocommit = True
        with connection.cursor() as cursor:
            cursor.execute(
                """
                select k.id, k.title, k.source_name, k.source_url, k.content, k.keywords,
                       k.legal_basis, k.purpose, k.verification_status, k.jurisdiction_code,
                       k.effective_from, k.effective_to, k.content_hash, k.source_version,
                       p.id, p.provision_key, p.heading, p.content
                from knowledge_documents k
                left join legal_document_versions v
                  on v.source_registry_id = k.source_registry_id
                 and k.source_version = concat(v.external_id, ':', substring(v.content_hash from 1 for 16))
                 and v.status = 'ACTIVE'
                left join legal_provisions p on p.legal_document_version_id = v.id
                where k.purpose = any(%s)
                  and k.verification_status = any(%s)
                order by k.id, p.id
                """,
                (list(ALLOWED_PURPOSES), list(ALLOWED_STATUSES)),
            )
            rows = cursor.fetchall()
    documents: list[IndexedDocument] = []
    for row in rows:
        try:
            documents.append(source_for_row(row))
        except ValueError:
            # Fail closed per document: malformed or stale sources are never indexed.
            continue
    return documents


def opensearch_client():
    try:
        import boto3
        from opensearchpy import AWSV4SignerAuth, OpenSearch, RequestsHttpConnection
    except ImportError as exception:
        raise RuntimeError("Install requirements.txt before synchronizing OpenSearch") from exception
    host = os.getenv("OPENSEARCH_HOST", "").replace("https://", "").replace("http://", "").strip("/")
    if not host:
        raise RuntimeError("OPENSEARCH_HOST is required")
    region = os.getenv("OPENSEARCH_REGION", "ap-northeast-2")
    service = os.getenv("OPENSEARCH_SERVICE", "aoss")
    credentials = boto3.Session().get_credentials()
    if credentials is None:
        raise RuntimeError("AWS credentials are required for OpenSearch synchronization")
    return OpenSearch(
        hosts=[{"host": host, "port": 443}],
        http_auth=AWSV4SignerAuth(credentials, region, service),
        use_ssl=True,
        verify_certs=True,
        connection_class=RequestsHttpConnection,
        timeout=30,
        max_retries=2,
        retry_on_timeout=True,
    )


def configure_pipelines(client) -> None:
    model_id = os.getenv("OPENSEARCH_NEURAL_MODEL_ID", "").strip()
    ingest_pipeline = os.getenv("OPENSEARCH_INGEST_PIPELINE", "knowledge-text-embedding").strip()
    search_pipeline = os.getenv("OPENSEARCH_SEARCH_PIPELINE", "knowledge-hybrid-rerank").strip()
    if not model_id or not ingest_pipeline or not search_pipeline:
        raise RuntimeError("Neural model, ingest pipeline, and search pipeline are required")
    client.transport.perform_request(
        "PUT",
        f"/_ingest/pipeline/{ingest_pipeline}",
        body={
            "description": "Generate governed knowledge embeddings",
            "processors": [
                {
                    "text_embedding": {
                        "model_id": model_id,
                        "field_map": {"embeddingText": "embedding"},
                    }
                }
            ],
        },
    )
    client.transport.perform_request(
        "PUT",
        f"/_search/pipeline/{search_pipeline}",
        body={
            "description": "Normalize and combine BM25 and vector retrieval diagnostics",
            "phase_results_processors": [
                {
                    "normalization-processor": {
                        "normalization": {"technique": "min_max"},
                        "combination": {
                            "technique": "arithmetic_mean",
                            "parameters": {"weights": [0.4, 0.6]},
                        },
                    }
                }
            ],
        },
    )


def configure_indices(client) -> None:
    dimension = int(os.getenv("OPENSEARCH_VECTOR_DIMENSION", "1024"))
    for purpose in sorted(ALLOWED_PURPOSES):
        name = purpose_index_name(purpose)
        if client.indices.exists(index=name):
            continue
        client.indices.create(
            index=name,
            body={
                "settings": {"index": {"knn": True}},
                "mappings": {
                    "dynamic": "strict",
                    "properties": {
                        "documentId": {"type": "long"},
                        "title": {"type": "text"},
                        "provisionKey": {"type": "text"},
                        "heading": {"type": "text"},
                        "sourceName": {"type": "keyword"},
                        "sourceUrl": {"type": "keyword", "index": False},
                        "content": {"type": "text"},
                        "keywords": {"type": "text"},
                        "legalBasis": {"type": "text"},
                        "purpose": {"type": "keyword"},
                        "verificationStatus": {"type": "keyword"},
                        "jurisdictionCode": {"type": "keyword"},
                        "effectiveFrom": {"type": "date"},
                        "effectiveTo": {"type": "date"},
                        "contentHash": {"type": "keyword"},
                        "provisionContentHash": {"type": "keyword"},
                        "sourceVersion": {"type": "keyword"},
                        "embeddingText": {"type": "text", "index": False},
                        "embedding": {
                            "type": "knn_vector",
                            "dimension": dimension,
                            "method": {
                                "name": "hnsw",
                                "space_type": "cosinesimil",
                                "engine": "faiss",
                            },
                        },
                    },
                },
            },
        )


def delete_stale_index_documents(client, documents: list[IndexedDocument]) -> None:
    ids_by_purpose = {
        purpose: [document.index_id for document in documents if document.purpose == purpose]
        for purpose in ALLOWED_PURPOSES
    }
    for purpose, current_ids in ids_by_purpose.items():
        query = (
            {"bool": {"must_not": [{"ids": {"values": current_ids}}]}}
            if current_ids
            else {"match_all": {}}
        )
        client.delete_by_query(
            index=purpose_index_name(purpose),
            body={"query": query},
            conflicts="proceed",
            refresh=True,
        )


def synchronize() -> int:
    if os.getenv("OPENSEARCH_SYNC_ENABLED", "false").lower() != "true":
        raise RuntimeError("Set OPENSEARCH_SYNC_ENABLED=true to synchronize governed knowledge")
    documents = load_documents()
    client = opensearch_client()
    configure_pipelines(client)
    configure_indices(client)
    try:
        from opensearchpy.helpers import bulk
    except ImportError as exception:
        raise RuntimeError("Install requirements.txt before synchronizing OpenSearch") from exception
    actions = (
        {
            "_op_type": "index",
            "_index": purpose_index_name(document.purpose),
            "_id": document.index_id,
            "_source": document.source,
        }
        for document in documents
    )
    bulk(
        client,
        actions,
        pipeline=os.getenv("OPENSEARCH_INGEST_PIPELINE", "knowledge-text-embedding"),
        chunk_size=int(os.getenv("OPENSEARCH_BULK_SIZE", "100")),
        raise_on_error=True,
    )
    delete_stale_index_documents(client, documents)
    return len(documents)


if __name__ == "__main__":
    print(f"Indexed {synchronize()} governed knowledge documents")
