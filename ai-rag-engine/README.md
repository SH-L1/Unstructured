# Python AI/RAG Engine

이 폴더는 민원 분석 및 RAG 기반 공문 초안 생성을 검증하기 위한 Python 로컬 엔진입니다.

## 포함 내용

- `main.py`: DB 또는 Markdown 기반 RAG 문서를 읽고 OpenAI API로 민원 분석 및 공문 초안을 생성합니다.
- `test_db_connection.py`: `.env`의 PostgreSQL 접속 정보로 DB 연결을 확인합니다.
- `insert_knowledge_documents.py`: `data/knowledge` 하위 Markdown 문서를 Spring 백엔드의 `knowledge_documents` 테이블에 적재합니다.
- `data/knowledge`: 법령, 조례, 매뉴얼 Markdown 문서입니다.
- `data/samples`: 샘플 민원 JSON입니다.

## 실행 준비

```powershell
cd ai-rag-engine
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

`.env`에는 개인 OpenAI API Key와 DB 접속 정보를 설정합니다. `.env`는 Git에 올리지 않습니다.

## DB 연결 테스트

DB가 실행 중인 팀원 PC에서 실행합니다.

```powershell
.venv\Scripts\python.exe test_db_connection.py
```

## RAG 문서 DB 적재

Spring 백엔드 실제 테이블인 `knowledge_documents`에 Markdown 문서들을 저장합니다.

```powershell
.venv\Scripts\python.exe insert_knowledge_documents.py
```

같은 제목의 문서가 이미 있으면 새로 중복 삽입하지 않고 기존 데이터를 업데이트합니다.

## 로컬 AI/RAG 실행

```powershell
.venv\Scripts\python.exe main.py
```

현재 `main.py`는 DB 연결이 가능하면 `knowledge_documents` 테이블을 먼저 읽고, DB 연결이 실패하면 `data/knowledge`의 Markdown 파일을 읽는 fallback 방식입니다.
