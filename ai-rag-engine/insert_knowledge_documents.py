import os
import re
from pathlib import Path

import psycopg2
from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parent
KNOWLEDGE_DIR = BASE_DIR / "data" / "knowledge"

DOCUMENT_TYPE_BY_FOLDER = {
    "manual": "MANUAL",
    "national_law": "LAW",
    "ordinance": "ORDINANCE",
}


def get_db_config():
    load_dotenv()
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": os.getenv("DB_PORT", "5432"),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": os.getenv("DB_USER", "complaint_user"),
        "password": os.getenv("DB_PASSWORD", "complaint_pass"),
    }


def extract_title(content, fallback):
    for line in content.splitlines():
        stripped = line.strip()
        if stripped.startswith("# "):
            return stripped[2:].strip()[:200]
    return fallback[:200]


def extract_section(content, heading):
    pattern = rf"^##\s+{re.escape(heading)}\s*$([\s\S]*?)(?=^##\s+|\Z)"
    match = re.search(pattern, content, flags=re.MULTILINE)
    if not match:
        return ""
    return match.group(1).strip()


def compact_text(text, max_length):
    text = re.sub(r"\s+", " ", text).strip()
    return text[:max_length]


def infer_legal_basis(document_type, title):
    if document_type in {"LAW", "ORDINANCE"}:
        return title
    return None


def split_markdown_chunks(content):
    sections = re.split(r"(?=^##\s+)", content, flags=re.MULTILINE)
    chunks = []
    current = ""

    for section in sections:
        section = section.strip()
        if not section:
            continue
        candidate = f"{current}\n\n{section}".strip() if current else section
        if len(candidate) <= 1800:
            current = candidate
        else:
            if current:
                chunks.append(current)
            current = section

    if current:
        chunks.append(current)

    return chunks or [content]


def load_knowledge_documents():
    if not KNOWLEDGE_DIR.exists():
        raise FileNotFoundError(f"Knowledge document folder not found: {KNOWLEDGE_DIR}")

    documents = []

    for file_path in sorted(KNOWLEDGE_DIR.rglob("*.md")):
        content = file_path.read_text(encoding="utf-8").strip()
        if not content:
            continue

        folder_name = file_path.parent.name
        document_type = DOCUMENT_TYPE_BY_FOLDER.get(folder_name, "SYNTHETIC")
        title = extract_title(content, file_path.stem)
        keywords = compact_text(extract_section(content, "적용 민원 유형"), 500) or title
        legal_basis = infer_legal_basis(document_type, title)
        chunks = split_markdown_chunks(content)

        documents.append({
            "document_type": document_type,
            "title": title,
            "source_name": title,
            "source_url": str(file_path.relative_to(BASE_DIR)).replace("\\", "/"),
            "content": content,
            "keywords": keywords,
            "legal_basis": legal_basis,
            "chunks": chunks,
        })

    if not documents:
        raise ValueError("No Markdown knowledge documents found.")

    return documents


def upsert_knowledge_document(cursor, document):
    cursor.execute(
        "SELECT id FROM knowledge_documents WHERE title = %s ORDER BY id LIMIT 1;",
        (document["title"],),
    )
    existing = cursor.fetchone()

    if existing:
        document["id"] = existing[0]
        cursor.execute(
            """
            UPDATE knowledge_documents SET
                document_type = %(document_type)s,
                source_name = %(source_name)s,
                source_url = %(source_url)s,
                content = %(content)s,
                keywords = %(keywords)s,
                legal_basis = %(legal_basis)s,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = %(id)s
            RETURNING id;
            """,
            document,
        )
        return cursor.fetchone()[0]

    cursor.execute(
        """
        INSERT INTO knowledge_documents (
            document_type, title, source_name, source_url, content, keywords,
            legal_basis, created_at, updated_at
        )
        VALUES (
            %(document_type)s, %(title)s, %(source_name)s, %(source_url)s,
            %(content)s, %(keywords)s, %(legal_basis)s, CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        RETURNING id;
        """,
        document,
    )
    return cursor.fetchone()[0]


def upsert_knowledge_chunks(cursor, document_id, document):
    cursor.execute(
        "UPDATE knowledge_document_chunks SET active = false, updated_at = CURRENT_TIMESTAMP WHERE knowledge_document_id = %s;",
        (document_id,),
    )

    saved = 0
    for index, content in enumerate(document["chunks"]):
        chunk = {
            "knowledge_document_id": document_id,
            "chunk_index": index,
            "content": content,
            "keywords": document["keywords"],
            "legal_basis": document["legal_basis"],
            "token_count": len(content.split()),
        }
        cursor.execute(
            """
            INSERT INTO knowledge_document_chunks (
                knowledge_document_id, chunk_index, content, keywords, legal_basis,
                token_count, active, created_at, updated_at
            )
            VALUES (
                %(knowledge_document_id)s, %(chunk_index)s, %(content)s,
                %(keywords)s, %(legal_basis)s, %(token_count)s, true,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (knowledge_document_id, chunk_index)
            DO UPDATE SET
                content = EXCLUDED.content,
                keywords = EXCLUDED.keywords,
                legal_basis = EXCLUDED.legal_basis,
                token_count = EXCLUDED.token_count,
                active = true,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id;
            """,
            chunk,
        )
        cursor.fetchone()
        saved += 1

    return saved


def sync_knowledge_documents(verbose=True):
    db_config = get_db_config()
    documents = load_knowledge_documents()

    if verbose:
        print("[knowledge sync start]")
        print(f"- folder: {KNOWLEDGE_DIR}")
        print(f"- documents: {len(documents)}")
        print(f"- DB: {db_config['host']}:{db_config['port']}/{db_config['dbname']}")

    saved_ids = []
    chunk_count = 0

    with psycopg2.connect(**db_config) as conn:
        with conn.cursor() as cursor:
            for document in documents:
                saved_id = upsert_knowledge_document(cursor, document)
                saved_ids.append(saved_id)
                chunk_count += upsert_knowledge_chunks(cursor, saved_id, document)
                if verbose:
                    print(f"- saved document id={saved_id}, chunks={len(document['chunks'])}, title={document['title']}")
        conn.commit()

    if verbose:
        print(f"[knowledge sync done] documents={len(saved_ids)}, chunks={chunk_count}")

    return {
        "documents": len(saved_ids),
        "chunks": chunk_count,
        "document_ids": saved_ids,
    }


def main():
    sync_knowledge_documents(verbose=True)


if __name__ == "__main__":
    main()
