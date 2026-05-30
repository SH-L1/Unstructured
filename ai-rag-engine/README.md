# Python AI/RAG Engine

이 폴더는 민원 분석 및 RAG 기반 공문 초안 생성을 검증하기 위한 Python 로컬 엔진입니다.

## 포함 내용

- `main.py`: `data/knowledge` Markdown 문서를 PostgreSQL에 자동 동기화한 뒤 DB 기반 RAG 문서를 읽고 OpenAI API로 민원 분석 및 공문 초안을 생성합니다.
- `test_db_connection.py`: `.env`의 PostgreSQL 접속 정보로 DB 연결을 확인합니다.
- `insert_knowledge_documents.py`: `data/knowledge` 하위 Markdown 문서를 Spring 백엔드의 `knowledge_documents`, `knowledge_document_chunks` 테이블에 적재합니다.
- `law_api_client.py`: 법제처 국가법령정보 공동활용 Open API로 국가법령/자치법규를 검색합니다.
- `department_router.py`: 과거 전자민원 처리부 엑셀을 기반으로 유사 민원과 담당 부서를 추천합니다.
- `data/knowledge`: 법령, 조례, 매뉴얼 Markdown 문서입니다.
- `data/samples`: 샘플 민원 JSON입니다.

샘플 민원은 본문만 입력하면 됩니다. `id`, `category`, `department`는 직접 적지 않아도 되며, `main.py`가 민원 ID를 자동 생성하고 OpenAI API로 분석 결과를 생성합니다.

```json
[
  {
    "text": "학교 앞 횡단보도 근처 도로가 갈라져 있어 학생들이 등하교할 때 위험해 보입니다."
  }
]
```

첨부 이미지가 있는 민원은 `image_path` 또는 `image_paths`를 함께 넣을 수 있습니다. 경로는 `ai-rag-engine` 폴더 기준 상대 경로나 절대 경로를 사용할 수 있습니다.

```json
[
  {
    "text": "학교 앞 도로가 파손되어 위험합니다.",
    "image_path": "data/samples/images/road_damage_001.jpg"
  },
  {
    "text": "공터에 폐가구와 쓰레기가 방치되어 있습니다.",
    "image_paths": [
      "data/samples/images/waste_001.jpg",
      "data/samples/images/waste_002.jpg"
    ]
  }
]
```

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

Spring 백엔드 실제 테이블인 `knowledge_documents`, `knowledge_document_chunks`에 Markdown 문서들을 저장합니다. `main.py` 일반 실행 시에도 이 동기화가 먼저 수행됩니다.

```powershell
.venv\Scripts\python.exe insert_knowledge_documents.py
.venv\Scripts\python.exe main.py --ingest-only
```

같은 제목의 문서가 이미 있으면 새로 중복 삽입하지 않고 기존 데이터를 업데이트합니다.

DB에 적재된 문서가 RAG 검색에 반영되는지만 확인하려면 LLM 호출 없이 다음 명령을 실행합니다.

```powershell
.venv\Scripts\python.exe main.py --verify-rag-only
```

## 법제처 Open API 검색 테스트

`.env`에 `LAW_API_OC` 값을 설정한 뒤 실행합니다.

```powershell
.venv\Scripts\python.exe law_api_client.py 도로법
.venv\Scripts\python.exe law_api_client.py 폐기물관리법
```

이 파일은 국가법령과 자치법규 검색 결과를 `main.py`의 RAG 결과와 합치기 쉬운 형태로 반환하는 `search_law_documents()` 함수도 제공합니다.

## 과거 민원 기반 부서 추천 테스트

```powershell
.venv\Scripts\python.exe department_router.py "탕정역 앞 불법주차 때문에 우회전이 어렵습니다."
```

전자민원 처리부 엑셀 파일은 기본적으로 `data/department_history/complaint_department_history.xlsx`를 사용합니다. 다른 파일을 쓰려면 `.env`의 `DEPARTMENT_HISTORY_XLSX` 값을 변경합니다.

과거 민원 이력 엑셀은 실제 민원 정보가 포함될 수 있으므로 Git에 올리지 않습니다. 각자 로컬 환경에서만 `data/department_history/complaint_department_history.xlsx` 경로에 파일을 배치해서 사용합니다.

## 로컬 AI/RAG 실행

```powershell
.venv\Scripts\python.exe main.py
```

현재 `main.py`는 DB 연결이 가능하면 `data/knowledge` Markdown 파일을 먼저 DB에 동기화하고, `knowledge_documents` 테이블을 읽습니다. DB 연결이 실패하면 `data/knowledge`의 Markdown 파일을 읽는 fallback 방식입니다.

또한 `.env`에 `LAW_API_OC`가 설정되어 있으면 민원 분석 결과를 바탕으로 법제처 Open API를 호출해 국가법령/자치법규 검색 결과를 RAG 근거에 함께 포함합니다. 법제처 API 호출이 실패해도 기존 DB/Markdown RAG 흐름은 계속 진행됩니다.
