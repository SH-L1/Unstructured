# 근거 검증형 민원 처리 지원 시스템

아산시 전체 민원에 대한 답변 템플릿 생성을 목표로 하는 사람 검토 중심 지원 시스템입니다. AI는 복합 이슈 분류, 담당 부서 후보, 근거 연결 초안만 제안하며 접수 여부, 최종 판단, 승인, 발송, 완료 처리는 사람이 수행합니다.

## 현재 구조

- 권위 서버는 `egov-boot-web`입니다. 중복 `backend` 구현은 활성 대상에서 제거했습니다.
- Spring은 상태 전이, 권한, 검증, 감사, 작업 조정, 민감정보 저장을 담당합니다.
- Python은 `ai-rag-engine`에서 비동기 작업자, 공공 API 수집기, OCR/첨부 파생 처리, 검색 인덱스 동기화만 담당합니다.
- 자동 발송과 자동 완료는 지원하지 않습니다. 승인 후에도 `POST /api/v1/complaints/{id}/complete`로 수동 완료 기록만 남깁니다.
- 기존 3개 유형 MVP 범위는 폐기하고, 아산시 전체 민원을 대상으로 확장했습니다. 아직 부서 규칙이 없는 유형은 `GENERAL`로 들어가며 조직/업무분장 데이터 적재 후 세분화합니다.

## 현재 반영된 핵심 변경

- 브라우저 API 키 제거, 서버 세션 기반 역할 도입
- `INTAKE`, `REVIEWER`, `APPROVER`, `KNOWLEDGE_ADMIN`, `AUDITOR`, `ADMIN` 역할 분리
- 검토자와 승인자 동일인 금지
- 모든 변경 API에 `Idempotency-Key`, 엔티티 버전/`If-Match` 적용
- 부작용 `GET` 및 임의 상태 변경 API 차단
- 민원 원문과 민감정보를 제한 저장소로 분리
- 첨부파일 격리, 악성 파일 검사, OCR/PDF/HWP 추출, 비식별 파생본만 AI 사용
- PostgreSQL 작업 큐, 작업 임대, 재시도, 실패 기록, 감사 로그 구현
- 고정 RAG 점수 표현 제거
- 근거 스냅샷, AI 실행 기록, 구조화 초안 주장, 주장-근거 링크, 검증 결과, 사람 검토 이력 구현
- 데이터 마트용 raw/normalized/error/ingestion run 테이블 추가
- 국가법령 동기화와 아산시 자치법규 동기화를 분리

## 데이터 정책

현재 개발 기준의 최종 데이터 범위는 [docs/final-data-scope.md](docs/final-data-scope.md)를 우선한다. DB 구성과 파일 배치 경로는 [docs/current-db-and-file-layout.md](docs/current-db-and-file-layout.md)를 따른다. API 키는 `SGIS`, 공공데이터포털, 국가법령정보 단위로 관리하고, 각 API에서 실제로 가져올 세부 데이터는 문서에 정의된 수집기 기준을 따른다.

| 데이터 | 현재 방침 |
| --- | --- |
| 국가법령정보 공동활용 API | `sync_official_sources.py`로 조항 단위 저장. `NATIONAL`, `VERIFIED_OFFICIAL`, `legal_evidence_allowed=true` 조건을 통과한 경우만 법적 근거로 사용 |
| 아산시 자치법규 | `sync_local_ordinances.py`로 별도 저장. `jurisdiction_code=ASAN`, 시행일/공포일/조항 보존. 기본은 `legal_evidence_allowed=false` |
| 국민권익위 민원정책 Q&A | 절차 참고 `PROCEDURE`, 법적 근거 금지 |
| 국민권익위 민원빅데이터 2022~2025 | 경향/유사사례/키워드 참고 `HISTORICAL_CASE`, 법적 근거 금지 |
| 민원사무서식 Open API | 민원 서식/절차 참고 데이터로 사용 |
| AIHub 행정법/공공민원 LLM 데이터 | 학습/튜닝/문체 참고 후보. 법적 근거 또는 최종 판단 근거 금지 |
| AIHub 문서 이해 기반 시각요소 데이터 | 첨부/문서 이해 보조 후보. 법적 근거 금지 |
| 새올 전자민원창구 | 2021년 이후 비식별 데이터만 사용. 담당 부서 추천/문체 참고에 한정 |
| 아산시 고시공고 | 1차 범위에서 제외. 검색 오염 가능성이 높아 RAG/DB 적재하지 않음 |
| 아산시 민원편람/민원사무편람 | 공개 절차 자료로 수집 대상. `PROCEDURE`, 법적 근거 금지 |
| 아산시 조직도/업무분장 | 사용자가 수집 후 `organization_units`, `assignment_rules`로 적재 예정 |
| GIS/공간 데이터 | 위치 후보 검증용 별도 공간 데이터 마트로 구축 예정 |

## 최종 확정 데이터 범위

현재 단계에서는 확보 완료 또는 API 키 확보가 끝난 데이터만 사용한다.

API 키 사용 데이터:

- SGIS: 아산시 행정구역, 읍면동, 법정동 경계와 관할 판정용 geometry
- 공공데이터포털: 전국도시공원정보표준데이터, 전국주차장정보표준데이터, 행정안전부 CCTV정보 조회서비스, 전국주정차금지(지정)구역표준데이터, 국민권익위원회 민원정책 Q&A, 2022~2025 국민권익위원회 민원빅데이터 분석정보
- 국가법령정보: 국가법령 조항과 아산시 자치법규 본문

다운로드 완료 데이터:

- `202605_건물DB_전체분(주소정보)`
- `2018~2026 민원편람`
- `현행 자치법규 리스트`
- `아산시 새올전자민원창구 공개 상담민원`
- `문서 이해 기반 시각요소 생성 데이터`
- `공공 민원 상담 LLM 사전학습 및 Instruction Tuning 데이터`
- `행정법 LLM 사전학습 및 Instruction Tuning 데이터`
- `아산시청 조직도`

법적 근거는 국가법령정보 API에서 가져온 국가법령 조항만 허용한다. 아산시 자치법규는 국가법령정보 API로 수집하지만 현재는 참고용 또는 부서 확인용으로만 사용한다. 부족한 내부 GIS, 고시공고, 별도 인허가/관리대장 데이터는 현재 개발 범위에서 제외한다.

## 상태 모델

```text
RECEIVED -> TRIAGE_REVIEW -> DRAFT_REVIEW -> APPROVAL_PENDING -> APPROVED -> COMPLETED
```

차단 상태:

```text
NEEDS_LOCATION
NEEDS_JURISDICTION
EVIDENCE_INSUFFICIENT
CONFLICT_DETECTED
PROCESSING_FAILED
```

## 공개 V1 API

```text
POST   /api/v1/complaints
GET    /api/v1/complaints/{id}
POST   /api/v1/complaints/{id}/analysis-runs
POST   /api/v1/complaints/{id}/draft-runs
GET    /api/v1/runs/{id}
POST   /api/v1/issues/{id}/location-confirmations
POST   /api/v1/drafts/{id}/reviews
POST   /api/v1/drafts/{id}/approvals
POST   /api/v1/complaints/{id}/complete
POST   /api/v1/complaints/{id}/attachments
GET    /api/v1/complaints/{id}/attachments
DELETE /api/v1/complaints/{id}/attachments/{attachmentId}
```

## 로컬 실행

Java 17과 PostgreSQL이 필요합니다.

```powershell
cd egov-boot-web
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dashboard-h2
```

대시보드:

```text
http://localhost:8081/dashboard
```

Python 작업자:

```powershell
cd ai-rag-engine
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
.venv\Scripts\python.exe worker.py
```

공식 출처/보조 출처 수집은 `.env`에서 명시적으로 활성화한 뒤 실행합니다.

```powershell
cd ai-rag-engine
.venv\Scripts\python.exe sync_official_sources.py
.venv\Scripts\python.exe sync_local_ordinances.py
.venv\Scripts\python.exe sync_auxiliary_sources.py
.venv\Scripts\python.exe knowledge_maintenance.py
```

## 검증

```powershell
cd egov-boot-web
mvn test

cd ..
ai-rag-engine\.venv\Scripts\python.exe -m compileall -q ai-rag-engine tools
cd ai-rag-engine
.venv\Scripts\python.exe -m unittest test_sync_official_sources.py test_sync_local_ordinances.py test_sync_auxiliary_sources.py test_sync_opensearch_indices.py test_worker.py test_provider_runtime.py test_knowledge_maintenance.py
.venv\Scripts\python.exe evaluate_golden.py data/evaluation/golden_cases.full.json data/evaluation/predictions.full.json --require-full
cd ..
git diff --check
```

최근 확인:

- `test_sync_local_ordinances.py`, `test_sync_official_sources.py` 통과
- `sync_local_ordinances.py`, `sync_official_sources.py`, `law_api_client.py` 구문 검사 통과
- 이전 대상 Java 테스트는 통과했으나 Docker 부재로 Testcontainers 기반 Flyway 검증은 skip될 수 있습니다.

## 문서

- [PLAN.md](PLAN.md): 구현 계획과 남은 우선순위
- [URGENTMODIFY.md](URGENTMODIFY.md): URGENTMODIFY 반영 상태
- [SETUP_SUMMARY.md](SETUP_SUMMARY.md): 환경 변수와 실행 설정
- [docs/data-source-ingestion.md](docs/data-source-ingestion.md): 데이터 수집 정책
- [docs/db-schema-review.md](docs/db-schema-review.md): DB 스키마 검토
