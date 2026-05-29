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
    if document_type == "LAW":
        return title
    if document_type == "ORDINANCE":
        return title
    return None


def load_knowledge_documents():
    if not KNOWLEDGE_DIR.exists():
        raise FileNotFoundError(f"지식 문서 폴더가 없습니다: {KNOWLEDGE_DIR}")

    documents = []

    for file_path in sorted(KNOWLEDGE_DIR.rglob("*.md")):
        content = file_path.read_text(encoding="utf-8").strip()
        if not content:
            continue

        folder_name = file_path.parent.name
        document_type = DOCUMENT_TYPE_BY_FOLDER.get(folder_name, "SYNTHETIC")
        title = extract_title(content, file_path.stem)
        keywords = compact_text(extract_section(content, "적용 민원 유형"), 500)

        if not keywords:
            keywords = title

        documents.append({
            "document_type": document_type,
            "title": title,
            "source_name": title,
            "source_url": None,
            "content": content,
            "keywords": keywords,
            "legal_basis": infer_legal_basis(document_type, title),
        })

    if not documents:
        raise ValueError("적재할 Markdown 지식 문서가 없습니다.")

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
            document_type,
            title,
            source_name,
            source_url,
            content,
            keywords,
            legal_basis,
            created_at,
            updated_at
        )
        VALUES (
            %(document_type)s,
            %(title)s,
            %(source_name)s,
            %(source_url)s,
            %(content)s,
            %(keywords)s,
            %(legal_basis)s,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        RETURNING id;
        """,
        document,
    )
    return cursor.fetchone()[0]


def main():
    db_config = get_db_config()
    documents = load_knowledge_documents()

    print("[knowledge_documents 적재 시작]")
    print(f"- 문서 폴더: {KNOWLEDGE_DIR}")
    print(f"- 적재 대상: {len(documents)}개")
    print(f"- DB: {db_config['host']}:{db_config['port']}/{db_config['dbname']}")

    with psycopg2.connect(**db_config) as conn:
        with conn.cursor() as cursor:
            saved_ids = []
            for document in documents:
                saved_id = upsert_knowledge_document(cursor, document)
                saved_ids.append(saved_id)
                print(f"- 저장 완료: id={saved_id}, type={document['document_type']}, title={document['title']}")

        conn.commit()

    print(f"\nknowledge_documents 적재 완료: {len(saved_ids)}개")


if __name__ == "__main__":
    main()
