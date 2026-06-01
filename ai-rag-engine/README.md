# AI/RAG 민원 대응 자동화 엔진

이 폴더는 `Unstructured` 프로젝트 안에서 공유하는 Python 기반 AI/RAG 로컬 엔진입니다.

민원 본문과 첨부 이미지를 입력하면 OpenAI API로 민원을 정형화하고, DB/Markdown 지식 문서, 법제처 API, 국민권익위 API, 과거 민원 부서 이력을 함께 참고해 공무원 검토용 공문 초안과 처리 요약 파일을 생성합니다.

현재 위치:

```text
C:\Users\alex8\OneDrive\문서\GitHub\Unstructured\ai-rag-engine
```

## 1. 프로젝트 목표

최종 목표는 민원 접수부터 공무원 검토용 초안 생성까지의 흐름을 자동화하는 것입니다.

```text
민원 접수
→ 민원 본문/첨부 이미지 저장
→ LLM 기반 민원 정형화
→ 과거 민원 기반 담당 부서 추천
→ RAG 기반 법령/조례/매뉴얼 검색
→ 법제처/국민권익위 API 보조 검색
→ 공무원 검토용 공문 초안 생성
→ JSON/Markdown 결과 저장
→ Spring 백엔드 또는 웹 대시보드와 연동
```

초기 MVP는 다음 유형을 중심으로 검증했습니다.

```text
1. 불법 투기 / 생활폐기물 / 악취 민원
2. 도로 파손 / 포트홀 / 보행·차량 위험 민원
3. 불법주정차 / 견인 / 교통 불편 민원
4. 생활소음 / 선거유세 소음 민원
```

현재는 위 범위를 넘어 다양한 민원도 받을 수 있도록 법제처 API, 국민권익위 API, 과거 민원 이력 데이터를 보조 자료로 붙이는 구조로 확장 중입니다.

## 2. 전체 처리 흐름

`main.py`를 실행하면 `data/samples/sample_complaints.json`에 있는 민원을 순서대로 처리합니다.

```text
1. sample_complaints.json 로드
2. 민원 ID 자동 생성
3. 첨부 이미지가 있으면 이미지 분석
4. 과거 민원 이력 기반 담당 부서 후보 검색
5. OpenAI API로 민원 분석 JSON 생성
6. 분석 결과 오분류 보정
7. DB 또는 Markdown RAG 문서 로드
8. 키워드 기반 RAG 검색
9. 법제처 Open API로 국가법령/자치법규 검색
10. 국민권익위 민원빅데이터 API로 유사사례/연관어/핵심 키워드 검색
11. 국민권익위 민원정책 Q&A API로 유사 질의/처리기관/업무구분 검색
12. 검색 결과를 근거 자료로 합침
13. OpenAI API로 공무원 검토용 공문 초안 생성
14. JSON, 일반 보고서, 공무원 검토용 요약 파일 저장
```

핵심은 LLM이 혼자 판단하지 않게 만드는 것입니다. LLM은 민원을 구조화하고 초안을 작성하지만, 근거 자료는 DB/Markdown/API/과거 민원 이력에서 가져와 함께 전달합니다.

## 3. 주요 파일과 역할

```text
ai-rag-engine
├─ main.py
├─ department_router.py
├─ test_db_connection.py
├─ insert_knowledge_documents.py
├─ requirements.txt
├─ .env
├─ .env.example
├─ README.md
├─ data
│  ├─ API
│  │  ├─ law_api_client.py
│  │  ├─ complaint_bigdata_api_client.py
│  │  └─ policy_qna_api_client.py
│  ├─ department_history
│  ├─ knowledge
│  │  ├─ manual
│  │  ├─ national_law
│  │  └─ ordinance
│  └─ samples
│     ├─ sample_complaints.json
│     └─ images
└─ result
```

주요 파일 설명:

- `main.py`: 전체 AI/RAG 민원 처리 흐름을 실행합니다.
- `department_router.py`: 과거 민원 처리부 엑셀 데이터를 이용해 유사 민원과 담당 부서 후보를 추천합니다.
- `test_db_connection.py`: `.env`의 PostgreSQL 접속 정보로 DB 연결을 확인합니다.
- `insert_knowledge_documents.py`: `data/knowledge` 하위 Markdown 문서를 Spring 백엔드의 `knowledge_documents` 테이블에 적재합니다.
- `data/API/law_api_client.py`: 법제처 국가법령정보 공동활용 Open API로 국가법령과 자치법규를 검색합니다.
- `data/API/complaint_bigdata_api_client.py`: 국민권익위 민원빅데이터 API로 유사사례, 연관어, 핵심 키워드를 검색합니다.
- `data/API/policy_qna_api_client.py`: 국민권익위 민원정책 Q&A API로 유사 질의, 처리기관, 업무구분을 검색합니다.
- `data/knowledge`: 로컬 Markdown 기반 RAG 문서입니다.
- `data/department_history`: 과거 민원 처리부 엑셀/CSV 파일을 두는 폴더입니다.
- `data/samples/sample_complaints.json`: 테스트할 민원 본문을 입력하는 파일입니다.
- `data/samples/images`: 테스트용 첨부 이미지를 두는 폴더입니다.
- `result`: `main.py` 실행 결과가 저장되는 폴더입니다.

## 4. 입력 데이터 작성 방식

민원 샘플은 `data/samples/sample_complaints.json`에 작성합니다.

기본적으로 민원 본문만 넣으면 됩니다. `id`, `category`, `department`는 직접 적지 않아도 됩니다. `main.py`가 `CPL-001`, `CPL-002`처럼 민원 ID를 자동 생성하고, OpenAI API가 민원 유형과 담당 부서 후보를 분석합니다.

예시:

```json
[
  {
    "text": "학교 앞 횡단보도 근처 도로가 갈라져 있어 학생들이 등하교할 때 위험해 보입니다."
  },
  {
    "text": "선거기간이라 유세차량 마이크 소리가 너무 커서 잠을 못 자겠습니다. 선문대 원룸 근처입니다."
  }
]
```

첨부 이미지가 있는 경우 `image_path` 또는 `image_paths`를 추가합니다.

```json
[
  {
    "text": "학교 앞 도로가 파손되어 위험합니다.",
    "image_path": "data/samples/images/road_damage_001.jpg"
  },
  {
    "text": "공터에 폐가구와 쓰레기가 방치되어 악취가 납니다.",
    "image_paths": [
      "data/samples/images/waste_001.jpg",
      "data/samples/images/waste_002.jpg"
    ]
  }
]
```

이미지 경로는 `ai-rag-engine` 기준 상대 경로 또는 절대 경로를 사용할 수 있습니다.

## 5. 환경 설정

PowerShell에서 `ai-rag-engine` 폴더로 이동합니다.

```powershell
cd C:\Users\alex8\OneDrive\문서\GitHub\Unstructured\ai-rag-engine
```

가상환경을 만들고 패키지를 설치합니다.

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
```

PowerShell 실행 정책 때문에 activate가 막히면 아래처럼 현재 PowerShell 세션에서만 허용할 수 있습니다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
.\.venv\Scripts\Activate.ps1
```

activate하지 않아도 아래처럼 직접 실행할 수 있습니다.

```powershell
.venv\Scripts\python.exe main.py
```

## 6. .env 설정

`.env.example`을 복사해 `.env`를 만듭니다.

```powershell
Copy-Item .env.example .env
```

`.env`에는 실제 인증키와 로컬 DB 정보를 넣습니다. `.env`는 절대 Git에 올리지 않습니다.

```env
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-4o-mini

DB_HOST=localhost
DB_PORT=5432
DB_NAME=complaintdb
DB_USER=complaint_user
DB_PASSWORD=complaint_pass

LAW_API_OC=your_law_open_api_key_here

COMPLAINT_BIGDATA_API_KEY=your_complaint_bigdata_api_key_here
COMPLAINT_BIGDATA_BASE_URL=http://apis.data.go.kr/1140100/minAnalsInfoView5

POLICY_QNA_API_KEY=
POLICY_QNA_BASE_URL=http://apis.data.go.kr/1140100/CivilPolicyQnaService

DEPARTMENT_HISTORY_XLSX=data/department_history/complaint_department_history.xlsx
```

`POLICY_QNA_API_KEY`는 별도 키가 있으면 넣고, 같은 공공데이터포털 인증키를 재사용하는 경우 비워둘 수 있습니다. 이 경우 코드가 `COMPLAINT_BIGDATA_API_KEY`를 대신 사용합니다.

## 7. RAG 데이터 구조

현재 RAG 데이터는 크게 네 종류입니다.

| 구분 | 위치 | 용도 |
| --- | --- | --- |
| Markdown 매뉴얼/법령/조례 | `data/knowledge` | 로컬 fallback RAG 문서 |
| PostgreSQL 문서 테이블 | `knowledge_documents` | Spring 백엔드와 공유할 DB 기반 RAG 문서 |
| 법제처 API | `data/API/law_api_client.py` | 국가법령/자치법규 실시간 검색 |
| 국민권익위 API | `data/API` | 유사사례, 연관어, 정책 Q&A 보조 검색 |

`main.py`는 DB 접속 정보가 있고 DB 연결이 가능하면 `knowledge_documents` 테이블을 먼저 읽습니다. DB 연결이 안 되면 `data/knowledge`의 Markdown 파일을 읽습니다.

```text
DB 사용 가능 → PostgreSQL knowledge_documents 사용
DB 사용 불가 → data/knowledge Markdown 사용
```

이 구조 덕분에 팀원 DB가 없어도 로컬 Markdown 기반으로 테스트할 수 있고, 팀원 DB가 실행 중이면 DB 기반 RAG로 전환할 수 있습니다.

## 8. 현재 Markdown 문서

현재 `data/knowledge`에는 다음 문서가 있습니다.

```text
manual/road_damage_manual.md
manual/waste_complaint_manual.md
national_law/road_act.md
national_law/waste_management_act.md
ordinance/asan_department_rule.md
ordinance/asan_waste_ordinance.md
```

이 문서들은 초기 MVP 검증용입니다. 다양한 민원에 대응하려면 소음, 불법주정차, 가로등, 공원시설, 하수도 등 업무 매뉴얼을 계속 추가해야 합니다.

## 9. DB 연결 테스트

DB는 팀원 PC 또는 Docker PostgreSQL에서 실행 중이어야 합니다.

```powershell
.venv\Scripts\python.exe test_db_connection.py
```

성공하면 `SELECT NOW();` 결과가 출력됩니다.

DB 설정은 `.env`의 아래 값으로 읽습니다.

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=complaintdb
DB_USER=complaint_user
DB_PASSWORD=complaint_pass
```

## 10. Markdown RAG 문서 DB 적재

`data/knowledge` 하위 Markdown 문서를 Spring 백엔드의 `knowledge_documents` 테이블에 넣습니다.

```powershell
.venv\Scripts\python.exe insert_knowledge_documents.py
```

같은 제목의 문서가 이미 있으면 중복 삽입하지 않고 기존 데이터를 업데이트합니다.

이 작업은 DB가 있는 팀원 환경에서 실행하는 것이 좋습니다.

## 11. 법제처 API

법제처 API는 국가법령과 자치법규를 검색하는 데 사용합니다.

테스트:

```powershell
.venv\Scripts\python.exe data\API\law_api_client.py 도로법
.venv\Scripts\python.exe data\API\law_api_client.py 폐기물관리법
.venv\Scripts\python.exe data\API\law_api_client.py "아산시 도로교통법"
```

역할:

- 국가법령 검색
- 자치법규 검색
- 법령/조례 상세 내용 일부 조회
- 검색 결과 캐싱
- `main.py`의 RAG 근거 자료에 추가

캐시 파일:

```text
.cache/law_api_details.json
```

한 번 조회한 법령/조례 상세 내용은 캐시에 저장되어 다음 실행 때 반복 호출을 줄입니다.

## 12. 국민권익위 민원빅데이터 API

민원빅데이터 API는 법적 근거가 아니라 보조 분석 자료로 사용합니다.

테스트:

```powershell
.venv\Scripts\python.exe data\API\complaint_bigdata_api_client.py "선거 유세 마이크 소음"
```

현재 MVP에서 주로 쓰는 정보:

- 유사사례
- 연관어
- 핵심 키워드

이 데이터는 민원 분류와 초안 작성의 참고자료입니다. 법령/조례처럼 직접적인 법적 근거로 단정하지 않습니다.

## 13. 국민권익위 민원정책 Q&A API

민원정책 Q&A API는 유사 질의, 처리기관, 업무구분을 참고하기 위해 사용합니다.

테스트:

```powershell
.venv\Scripts\python.exe data\API\policy_qna_api_client.py "선거 유세 마이크 소음"
```

주의:

- 현재 API가 공공데이터포털에서 복구중이거나 권한 상태에 따라 `403 Forbidden`이 날 수 있습니다.
- 실패해도 `main.py` 전체 실행은 중단되지 않고 해당 API만 건너뜁니다.
- 이 결과는 담당 부서를 확정하는 자료가 아니라 참고 자료입니다.

## 14. 과거 민원 기반 부서 추천

과거 민원 처리부 엑셀을 기반으로 현재 민원과 유사한 민원을 찾고 담당 부서 후보를 추천합니다.

테스트:

```powershell
.venv\Scripts\python.exe department_router.py "탕정역 앞 불법주차 때문에 우회전이 어렵습니다."
```

데이터 위치:

```text
data/department_history
```

기본 설정:

```env
DEPARTMENT_HISTORY_XLSX=data/department_history/complaint_department_history.xlsx
```

과거 민원 데이터는 실제 민원 정보가 포함될 수 있으므로 Git에 올리지 않는 것이 원칙입니다.

## 15. main.py 실행

샘플 민원을 작성한 뒤 실행합니다.

```powershell
.venv\Scripts\python.exe main.py
```

또는 가상환경을 activate한 상태라면:

```powershell
py .\main.py
```

실행 결과는 민원 1건당 3개 파일로 `result` 폴더에 저장됩니다.

```text
result/output_CPL-001.json
result/output_CPL-001_report.md
result/output_CPL-001_official_review.md
```

파일별 용도:

| 파일 | 용도 |
| --- | --- |
| `output_CPL-001.json` | 개발자/DB 연동용 구조화 결과 |
| `output_CPL-001_report.md` | 전체 분석 과정을 사람이 볼 수 있는 보고서 |
| `output_CPL-001_official_review.md` | 공무원이 핵심 확인 사항과 공문 초안을 빠르게 검토하는 문서 |

## 16. 공무원 검토용 문서 구성

`output_CPL-001_official_review.md`는 최종 발송 문서가 아니라 검토용 문서입니다.

포함 내용:

```text
1. 민원 접수 내용
2. AI 분석 요약
3. 처리 절차 요약
4. 공무원 확인 사항
5. 첨부 이미지 분석 요약
6. 주요 근거 문서
7. 공문 초안
```

공무원은 이 파일에서 다음을 확인하면 됩니다.

- 민원 유형이 맞는지
- 담당 부서 후보가 맞는지
- 현장 확인이 필요한지
- 법령/조례/매뉴얼 근거가 민원과 직접 관련 있는지
- 초안에 처리 완료, 단속 완료, 과태료 부과 같은 단정 표현이 없는지

## 17. LLM 사용 위치

OpenAI API는 세 곳에서 사용합니다.

1. 첨부 이미지 분석
2. 민원 본문 정형화
3. 공문 초안 생성

민원 분석 결과 예시:

```json
{
  "category": "도로 파손",
  "summary": "학교 앞 횡단보도 근처 도로 갈라짐으로 인한 보행 위험 민원",
  "urgency": "High",
  "department": "도로관리과",
  "location_text": "학교 앞 횡단보도",
  "keywords": ["도로 파손", "도로 균열", "보행 위험", "학생"],
  "needs_field_check": true
}
```

## 18. 현재 안전장치

`main.py`에는 다음 안전장치가 들어 있습니다.

- 민원 ID 자동 생성
- 잘못된 JSON 입력 시 즉시 에러 출력
- 첨부 이미지 경로 검증
- 이미지 분석 실패 시 전체 실행 중단 방지
- DB 연결 실패 시 Markdown RAG로 fallback
- 법제처 API 실패 시 기존 RAG만으로 계속 진행
- 국민권익위 API 실패 시 해당 API만 건너뜀
- 생활소음/선거유세 소음 등 일부 오분류 후처리
- 공문 초안에서 처리 완료나 단속 완료를 단정하지 않도록 프롬프트 제한

## 19. 현재 한계

현재 프로젝트는 완성형 서비스가 아니라 MVP 검증 단계입니다.

주요 한계:

- 로컬 Markdown 매뉴얼이 아직 적습니다.
- 모든 민원 유형에 대한 업무 매뉴얼이 준비된 것은 아닙니다.
- 법제처 API는 법령/자치법규 검색에는 좋지만 실제 부서 업무 매뉴얼을 대체하지는 못합니다.
- 국민권익위 민원빅데이터 API는 유사사례와 키워드 참고용이지 공식 처리 절차 자료는 아닙니다.
- 과거 민원 부서 추천은 데이터 품질과 컬럼 구조에 따라 정확도가 달라집니다.
- 최종 담당 부서와 처리 방향은 공무원 검토가 필요합니다.

## 20. 팀원 역할 기준 실행 순서

DB가 없는 개인 개발자:

```text
1. .env에 OPENAI_API_KEY 설정
2. data/samples/sample_complaints.json에 민원 본문 작성
3. 필요하면 data/samples/images에 이미지 추가
4. main.py 실행
5. result 폴더의 결과 확인
```

DB가 있는 팀원:

```text
1. PostgreSQL 실행
2. .env에 DB 정보 설정
3. test_db_connection.py 실행
4. insert_knowledge_documents.py 실행
5. main.py 실행
6. Spring 백엔드와 knowledge_documents 테이블 연동 확인
```

API 테스트 담당:

```text
1. .env에 LAW_API_OC 설정
2. .env에 COMPLAINT_BIGDATA_API_KEY 설정
3. 필요하면 POLICY_QNA_API_KEY 설정
4. data/API 하위 클라이언트 개별 실행
5. main.py 실행 시 API 결과가 RAG 보조 자료에 합쳐지는지 확인
```

## 21. Spring 백엔드와의 연결 방향

현재 Python 엔진은 독립 실행형으로 동작합니다. Spring 백엔드와 연결하는 방식은 두 가지입니다.

빠른 MVP 방식:

```text
Spring Boot 백엔드
→ 민원 등록/조회/저장
→ Python AI/RAG 모듈 호출
→ JSON 결과 수신
→ 분석 결과와 공문 초안 DB 저장
```

장기 통합 방식:

```text
Spring Boot 내부에서
→ Mock LLM 또는 실제 LLM Client 호출
→ DB RAG 검색
→ 공문 초안 생성
```

현재는 Python 엔진이 이미 동작하므로 빠른 MVP 방식이 가장 안전합니다.

## 22. Git 관리 주의사항

Git에 올려도 되는 것:

```text
main.py
department_router.py
insert_knowledge_documents.py
test_db_connection.py
data/API/*.py
data/knowledge/*.md
data/samples/sample_complaints.json
.env.example
README.md
requirements.txt
```

Git에 올리지 말아야 하는 것:

```text
.env
.venv
.cache
result/output_*
실제 개인정보가 포함된 과거 민원 엑셀
실제 민원 첨부 이미지
```

인증키는 반드시 `.env`에만 넣고, `.env.example`에는 placeholder만 남깁니다.

## 23. 자주 나는 오류

JSON 입력 오류:

```text
json.decoder.JSONDecodeError
```

원인:

- `sample_complaints.json`에 객체 여러 개를 배열 없이 따로 작성함
- 쉼표 누락
- 문자열 따옴표 누락
- JSON 안에 주석 작성

정상 형식:

```json
[
  {
    "text": "첫 번째 민원"
  },
  {
    "text": "두 번째 민원"
  }
]
```

API 인증키 오류:

```text
LAW_API_OC가 없습니다
COMPLAINT_BIGDATA_API_KEY가 없습니다
```

원인:

- `.env`에 인증키가 없음
- `.env` 파일 위치가 `ai-rag-engine` 루트가 아님
- 키 이름을 다르게 적음

DB 연결 오류:

```text
connection refused
password authentication failed
```

원인:

- PostgreSQL 컨테이너가 꺼져 있음
- DB 이름/계정/비밀번호가 다름
- 5432 포트가 열려 있지 않음

## 24. 다음 확장 방향

우선순위:

```text
1. 소음/불법주정차/가로등/공원시설/하수도 매뉴얼 추가
2. 과거 민원 엑셀/CSV 파서 개선
3. Spring 백엔드의 민원 등록 API와 Python 엔진 연결
4. result JSON 구조를 백엔드 DTO와 맞추기
5. PostgreSQL 기반 RAG 검색 고도화
6. pgvector 또는 OpenSearch 기반 벡터 검색 도입 검토
7. 웹 대시보드에서 공무원 검토용 문서 확인
```

현재 목표는 모든 것을 한 번에 완성하는 것이 아니라, 민원 본문과 첨부 자료를 넣었을 때 공무원이 검토 가능한 초안과 근거 요약이 자동으로 생성되는 MVP 흐름을 안정화하는 것입니다.
