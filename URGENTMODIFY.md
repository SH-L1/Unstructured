# URGENTMODIFY 반영 상태

## 요약

URGENTMODIFY의 핵심 방향은 “자동 처리 시스템”이 아니라 “근거 검증형 민원 처리 지원 시스템”으로 전환하는 것이다. 현재 구현은 이 방향으로 전환되어 있으며, 남은 작업은 실제 아산시 운영 데이터 적재와 GIS/평가 체계 구축이다.

## 반영 완료

- 권위 서버를 `egov-boot-web`으로 통합
- 중복 `backend` 활성 구현 제거
- 브라우저 API 키 제거
- 세션 기반 역할 도입
- 검토자/승인자 분리
- 승인 없는 완료 차단
- 자동 발송/자동 완료 제거
- 부작용 `GET` 및 임의 상태 변경 API 차단
- 변경 API 멱등성 및 낙관적 잠금 적용
- 민원 상태와 차단 상태 모델 도입
- 민원 원문과 민감정보 분리 저장
- 첨부파일 격리, 악성 파일 검사, OCR/PDF/HWP 추출, 비식별 파생본 사용
- PostgreSQL 작업 큐, 임대, 재시도, 실패 기록 도입
- Python 작업자 제한 역할 도입
- AI 결과 JSON Schema 검증
- LLM 좌표/GeoJSON 생성 차단
- 고정 RAG 점수 제거
- 근거 스냅샷과 초안 주장-근거 링크 도입
- 검증 결과와 사람 검토/승인 감사 기록 도입
- OpenAI/Bedrock/Mock 공급자 추상화와 재시도/회로 차단 도입
- OpenSearch 목적별 파생 인덱스 구조 도입
- 150건 합성 골든 데이터와 평가 스크립트 추가
- 데이터 마트 테이블 추가

## 최근 추가 반영

### 아산시 전체 민원 범위 확장

초기 3개 유형 제한을 폐기하고 아산시 전체 민원 답변 템플릿 생성으로 범위를 확장했다. 현재 부서 규칙이 없는 유형은 `GENERAL`로 처리하며, 아산시 조직도/업무분장 적재 후 세분화한다.

### 아산시 자치법규 수집기 추가

`ai-rag-engine/sync_local_ordinances.py`를 추가했다.

정책:

- 법제처 자치법규 API `target=ordin` 사용
- `jurisdiction_code=ASAN`
- 공포일/시행일 보존
- 조항 단위 저장
- `legal_document_versions`, `legal_provisions`, `knowledge_documents`, `knowledge_purpose` 적재
- 기본 `legal_evidence_allowed=false`

자치법규는 공식 자료지만 국가법령과 검증 정책이 다르므로 자동 법적 근거로 쓰지 않는다.

### 새올 전자민원창구 이력 범위 결정

새올 데이터는 2021년 이후만 수집한다.

사유:

- 오래된 이력은 조직개편으로 담당 부서가 달라질 수 있음
- 법령/조례 개정 전 데이터가 현재 처리 기준과 충돌할 수 있음
- 최근 데이터가 담당 부서 추천 품질에 더 유효함

사용 범위:

- 비식별 처리 필수
- 담당 부서 추천과 문체 참고에만 사용
- 법적 근거 사용 금지

### 고시공고 제외 결정

아산시 현재 고시공고 5459건은 1차 범위에서 제외한다.

사유:

- 운행정지명령 반송, 공시송달, 보조금 서류, 채용, 입찰, 체납 등 프로젝트와 무관하거나 위험한 문서가 많음
- 개별처분/개별 대상자 문서가 RAG에 들어가면 검색 오염 가능성이 큼
- 선별, 첨부 OCR, 개인정보 제거, 적용기간 검증 비용이 큼

추후 범위:

- 주정차 금지구역
- 도로 통행제한
- 단수/하수도 공사
- 안전통제
- 공원/하천/시설물 이용 제한

이런 현재 행정상태를 바꾸는 고시만 `LOCAL_NOTICE`로 별도 파이프라인을 만든다.

## 현재 데이터 확보 상태

확보 또는 사용 중:

- 국민권익위원회 민원정책 질의응답조회서비스
- 국민권익위원회 민원빅데이터 분석정보 API 2022
- 국민권익위원회 민원빅데이터 분석정보 API 2023
- 국민권익위원회 민원빅데이터 분석정보 API 2024
- 국민권익위원회 민원빅데이터 분석정보 API 2025
- 국가법령정보 공동활용 Open API
- 민원사무서식 Open API
- AIHub 행정법 LLM 사전학습 및 Instruction Tuning 데이터
- AIHub 공공 민원 상담 LLM 사전학습 및 Instruction Tuning 데이터
- AIHub 문서 이해 기반 시각요소 생성 데이터

수집 예정:

- 아산시 조직도/업무분장
- 아산시 민원편람/민원사무편람
- 아산시 새올 전자민원창구 2021년 이후 비식별 이력
- GIS/공간 데이터

제외:

- 아산시 전체 고시공고 일괄 수집

## DB 반영 상태

Flyway V1~V18 기준으로 URGENTMODIFY의 DB 구조 요구사항은 반영되어 있다.

- 지식/출처
- 조직/배정
- 민원 이슈/부서 작업/위치 후보
- 처리 작업/검색 실행/근거 스냅샷/AI 실행
- 초안 주장/주장-근거 링크/검증 결과/사람 검토
- 민감정보/첨부 분석
- 감사/멱등성
- 데이터 마트 raw/normalized/error/run 테이블
- GIS 공간 데이터 마트와 위치 후보 해석 run/candidate 테이블

단, DB 구조 반영과 실제 데이터 적재는 별개다. 실제 아산시 데이터는 후속 적재 작업이 필요하다.

## 남은 작업

1. 아산시 조직도/업무분장 적재
2. 아산시 민원편람/민원사무편람 수집 및 `PROCEDURE` 적재
3. 새올 2021년 이후 비식별 이력 적재
4. GIS/PostGIS 기반 위치 후보 검증
5. 자치법규 법적 근거 승격 정책
6. 실제 아산시 데이터 기반 골든 평가셋
7. 고시공고 `LOCAL_NOTICE` 선별 파이프라인은 후순위

## 검증 상태

최근 확인:

- `python -m py_compile sync_local_ordinances.py sync_official_sources.py data\API\law_api_client.py` 통과
- `python -m unittest test_sync_local_ordinances.py test_sync_official_sources.py` 통과
- 이전 Java 대상 테스트는 통과했으나 Docker가 없으면 Testcontainers Flyway 검증은 skip된다.
## 최종 확정 데이터 범위

최종 확정 범위는 [docs/final-data-scope.md](docs/final-data-scope.md)를 우선한다. 부족한 데이터는 일단 제외하고, 아래 API 키와 다운로드 완료 데이터만 기준으로 개발한다.

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

법적 근거는 국가법령정보 API에서 가져온 국가법령 조항만 허용한다. 아산시 자치법규는 국가법령정보 API로 수집하지만 현재는 참고용 또는 부서 확인용으로만 사용한다. 아직 확보하지 않은 내부 GIS, 고시공고, 별도 축산/공동주택/상하수도/도로관리 대장 데이터는 현재 개발 범위에서 제외한다.

## 2026-06-07 감사 보강 반영

이 섹션은 현재 코드 기준 최신 반영 상태다.

- AIHub ZIP 처리는 더 이상 manifest 적재에 그치지 않는다. `sync_local_file_data_mart.py`가 AIHub ZIP 내부의 JSON, JSONL, TXT, CSV, MD, XML 텍스트를 제한적으로 추출해 `STYLE_REFERENCE` 또는 `EVALUATION_TRAINING` 파생 자료로 적재한다. 단, AIHub 자료는 계속 법적 근거로 사용할 수 없다.
- 실제 민원 골든 평가기는 `allowedUses`, `forbiddenUses`, 승인된 익명화 검토, 승인된 라벨 검토, 익명화 입력 기준 재계산 `inputHash`를 모두 강제한다.
- 담당부서 추천은 승인된 부서 히스토리 XLSX, 2021년 이후 비식별 새올 공개민원, 아산시 민원편람 파일명을 보조 근거로 사용하는 결정론 라우터로 보강했다. 이 자료들은 담당부서 추천 보조용이며 법적 근거가 아니다.
- 초안 worker는 검증 실패 시 이미 제공된 공식 근거 후보 안에서만 1회 제한 재작성한다. 공식 근거 부재, PII, 관할/시행일 등 hard fail은 계속 차단한다.
- 검색 재순위와 OpenSearch 목적별 인덱싱은 `STYLE_REFERENCE`, `LOCAL_ORDINANCE_REFERENCE`, `EVALUATION_TRAINING`을 인식한다.
- 회귀 테스트는 실제 골든 안전 게이트, AIHub ZIP payload 추출, 담당부서 보조 라우팅, reranker 목적 가중치, worker 재작성, OpenSearch 목적명을 포함한다.

검증 명령:

```powershell
C:\Users\user\miniconda3\python.exe -m unittest discover -s . -p "test*.py"
mvn test
```

최근 확인 결과:

- Python: 40 tests passed.
- Java/Maven: BUILD SUCCESS, 49 tests run, 1 skipped. Docker/Testcontainers PostgreSQL 환경이 없어 PostgreSQL 전용 Flyway 테스트 1건은 skip되었다.
## 2026-06-08 Department Top-3 Human Selection

Frontend is handled separately, but the backend contract now supports the
planned interactive department selection step.

- `POST /api/v1/issues/{issueId}/department-confirmations` was added.
- Request body: `{"departmentCode":"ROAD"}`.
- Required headers: `Idempotency-Key`, `If-Match`.
- Allowed roles in session mode: `REVIEWER`, `ADMIN`.
- Complaint detail responses now include:
  - `issue.departmentCandidates`: legacy string list for simple UI rendering.
  - `issue.departmentCandidateDetails[]`: structured candidates with `code`,
    `status`, `recommendationReason`, `confirmedBy`, `score`, `selected`,
    `verified`.
- Draft generation is blocked until every issue has one human-selected and
  server-verified department candidate.
- Server verification checks that the selected department is within the issue
  Top-3 candidates, exists as an active department code, and does not conflict
  with deterministic pilot assignment rules.
- Failed selection is persisted as a `DEPARTMENT_SELECTION` verification failure,
  marks the candidate `REJECTED`, and leaves the complaint blocked for
  jurisdiction review instead of attempting draft rewriting.
- Python worker department routing now limits candidates to Top-3 and uses up
  to two validation rewrite attempts by default for draft schema/evidence-link
  repair only.

Frontend handoff:

- Render candidate cards from `departmentCandidateDetails`.
- Show score as diagnostic routing support, not as legal confidence.
- Let reviewer pick one candidate, then call the confirmation API.
- Disable `draft-runs` until all issues have `verified=true`.
- If the confirmation response has `workflowBlocker=NEEDS_JURISDICTION` or a
  `DEPARTMENT_SELECTION` failure, show the rejection reason and require a new
  selection or jurisdiction escalation.
