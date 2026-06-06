# DB 스키마 검토

Flyway가 유일한 스키마 권위다. Hibernate는 `ddl-auto=validate`로만 사용한다.

## 현재 마이그레이션

```text
V1  complaint schema
V2  audit logs
V3  legacy API users
V4  complaint refinements and chunks
V5  remove fixed RAG score
V6  trust workflow
V7  remove raw text from general complaint storage
V8  workflow audit events
V9  remove legacy API users
V10 version attachment changes
V11 seed clearly labeled synthetic demo organization and assignment rules
V12 preserve legal basis in immutable evidence snapshots
V13 clear legacy AI-generated GeoJSON
V14 store structured draft claim source document IDs
V15 preserve source document versions in knowledge and immutable evidence snapshots
V16 add source synchronization schedule, knowledge source registry link, job payload reference, and approved attachment derivative reference
V17 create data mart ingestion tables
V18 create GIS spatial data mart tables
```

## 핵심 테이블

### 지식과 출처

- `source_registry`
- `legal_document_versions`
- `legal_provisions`
- `legal_relations`
- `knowledge_documents`
- `knowledge_purpose`

국가법령은 `jurisdiction_code=NATIONAL`, 자치법규는 `jurisdiction_code=ASAN`으로 분리한다.

### 조직과 배정

- `organization_units`
- `assignment_rules`

현재 seed 데이터는 `SYNTHETIC_DEMO`로 명확히 표시된다. 실제 아산시 조직도/업무분장 적재 후 대체해야 한다.

### 민원 처리

- `complaints`
- `complaint_issues`
- `department_tasks`
- `location_candidates`
- `historical_complaints`

새올 이력은 2021년 이후 비식별 데이터만 `historical_complaints`에 적재한다.

### 작업과 근거

- `processing_jobs`
- `retrieval_runs`
- `evidence_snapshots`
- `ai_runs`

`evidence_snapshots`는 초안 생성 시점에 사용된 원문과 메타데이터의 불변 복사본이다.

### 초안과 검증

- `official_drafts`
- `draft_claims`
- `claim_evidence_links`
- `verification_results`
- `human_reviews`

모든 초안 주장은 하나 이상의 근거 스냅샷에 연결되어야 한다.

### 민감정보와 첨부

- `complaint_sensitive_payloads`
- `complaint_attachments`
- `attachment_analysis`

원본 첨부는 격리 저장소에 두고, 승인된 비식별 파생 데이터만 AI 입력으로 사용한다.

### 감사와 멱등성

- `audit_logs`
- `workflow_audit_events`
- `idempotency_records`

모든 변경 API는 멱등성 키와 버전 검사를 사용한다.

### 데이터 마트

- `data_mart_ingestion_runs`
- `data_mart_raw_records`
- `data_mart_normalized_records`
- `data_mart_load_errors`

API나 크롤러 응답은 raw로 보존하고, 정규화된 문서/레코드는 별도 연결한다.

## 필수 불변조건

- `complaints.version`과 `official_drafts.version`은 상태 전이를 보호한다.
- `processing_jobs(job_type, idempotency_key)`는 중복 작업을 방지한다.
- 외부 호출은 DB 트랜잭션 밖에서 수행한다.
- 작업 시작/성공/실패는 작업 행 잠금과 별도 커밋으로 기록한다.
- Python 작업자는 Spring 내부 서비스 토큰으로 작업을 claim/complete/fail 한다.
- 사람 검토와 승인은 서로 다른 사용자가 수행해야 한다.
- 모든 `GET`은 읽기 전용이어야 한다.
- `UNVERIFIED_LEGACY` 문서는 초안 근거로 쓰지 않는다.
- 국민권익위 Q&A, 민원빅데이터, 새올 이력, AIHub 데이터는 법적 근거로 쓰지 않는다.
- 국가법령만 현재 자동 법적 근거로 허용된다.
- 아산시 자치법규는 조항/시행일/관할을 저장하지만 기본적으로 `legal_evidence_allowed=false`다.
- 고시공고는 1차 범위에서 적재하지 않는다.
- OpenSearch 인덱스는 파생 검색 인덱스일 뿐 스키마 권위가 아니다.
- 검색 점수는 내부 진단값이며 신뢰도나 승인 근거로 표시하지 않는다.

## 운영 점검

`docs/db-check-queries.sql`로 운영 상태를 확인한다.

공유 환경에 적용된 Flyway 마이그레이션은 수정하지 않는다. 변경이 필요하면 새 버전 마이그레이션을 추가한다.
