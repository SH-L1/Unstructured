# 구현 계획과 단계

## 목표

아산시 전체 민원에 대해 근거 검증형 답변 템플릿을 생성한다. 시스템은 자동 민원 처리기가 아니라 검토자와 승인자를 보조하는 도구다. Spring은 권위 있는 상태와 검증을 담당하고, Python은 비동기 수집·검색·OCR·AI 작업자 역할만 수행한다.

## 완료된 전환

- `egov-boot-web`을 권위 서버로 고정
- 중복 `backend` 활성 구현 제거
- 세션 기반 역할과 CSRF 보호 도입
- 브라우저 API 키 제거
- 부작용 `GET` 및 임의 상태 변경 API 차단
- `Idempotency-Key`, `If-Match`, 엔티티 버전 기반 변경 API 처리
- 민원 상태 모델을 `RECEIVED -> TRIAGE_REVIEW -> DRAFT_REVIEW -> APPROVAL_PENDING -> APPROVED -> COMPLETED`로 제한
- 차단 상태 `NEEDS_LOCATION`, `NEEDS_JURISDICTION`, `EVIDENCE_INSUFFICIENT`, `CONFLICT_DETECTED`, `PROCESSING_FAILED` 도입
- 검토자와 승인자 동일인 금지
- 승인 없는 완료 차단
- 자동 발송/자동 완료 제거

## DB와 작업 큐

Flyway V1~V18 기준으로 다음 구조가 반영되어 있다.

- 지식/출처: `source_registry`, `legal_document_versions`, `legal_provisions`, `legal_relations`, `knowledge_purpose`
- 조직/배정: `organization_units`, `assignment_rules`
- 민원 분해: `complaint_issues`, `department_tasks`, `location_candidates`, `historical_complaints`
- 실행/근거: `processing_jobs`, `retrieval_runs`, `evidence_snapshots`, `ai_runs`
- 초안/검증: `draft_claims`, `claim_evidence_links`, `verification_results`, `human_reviews`
- 개인정보/첨부: `complaint_sensitive_payloads`, `attachment_analysis`
- 감사/멱등성: `audit_logs`, `idempotency_records`, `workflow_audit_events`
- 데이터 마트: `data_mart_ingestion_runs`, `data_mart_raw_records`, `data_mart_normalized_records`, `data_mart_load_errors`
- GIS 데이터 마트: `spatial_source_registry`, `spatial_admin_boundaries`, `spatial_address_points`, `spatial_road_segments`, `spatial_facilities`, `spatial_parking_restrictions`, `spatial_location_resolution_runs`, `spatial_location_candidates`

## 데이터 수집 계획

현재 개발 기준의 최종 데이터 범위는 [docs/final-data-scope.md](docs/final-data-scope.md)를 우선한다. 부족한 데이터는 일단 제외하고, SGIS·공공데이터포털·국가법령정보 API와 다운로드 완료 데이터만 사용한다.

### 1. 국가법령

`국가법령정보 공동활용 OPEN API`를 사용한다. `sync_official_sources.py`가 국가법령 `target=law`를 조항 단위로 저장한다.

- 저장 관할: `NATIONAL`
- 검증 상태: `VERIFIED_OFFICIAL`
- 법적 근거 허용: `true`
- 필수 검증: 출처, 조항, 시행일, 버전, 관할, 최신성

### 2. 아산시 자치법규

`sync_local_ordinances.py`를 추가했다. 법제처 자치법규 `target=ordin`으로 아산시 조례/규칙을 가져온다.

- 저장 관할: `ASAN`
- 공포일/시행일 보존
- 조항 단위 저장
- 검증 상태: `VERIFIED_INTERNAL`
- 법적 근거 허용: 기본 `false`

자치법규는 공식 출처이지만 국가법령과 관할·폐지·시행기간 검증 정책이 다르므로 별도 승인 전에는 자동 법적 근거로 쓰지 않는다.

### 3. 조직도와 업무분장

사용자가 별도 수집한다. 수집 후 `organization_units`, `assignment_rules`에 적재한다.

필수 필드:

- 부서 코드
- 부서명
- 팀명
- 담당 업무
- 대표 연락처
- 유효 시작일/종료일
- 조직개편 이력
- 민원 유형별 배정 규칙

### 4. 아산시 민원편람/민원사무편람

내부 SOP 공개본을 찾기 어렵기 때문에 공개 민원편람을 `PROCEDURE` 자료로 사용한다.

- 처리기간
- 구비서류
- 접수/처리 부서
- 민원 사무명
- 관련 법령/조례명

법적 근거가 아니라 절차 참고 자료로만 쓴다.

### 5. 새올 전자민원창구 이력

2021년 이후 데이터만 가져온다. 너무 오래된 데이터는 조직개편과 법령 개정으로 배정 품질을 떨어뜨릴 수 있다.

- 원문은 사용하지 않고 비식별 처리본만 저장
- 담당 부서 추천과 문체 참고에만 사용
- 법적 근거 금지
- 평가셋 후보 100~200건은 추후 분리

### 6. GIS/공간 데이터

위치 후보 검증을 위해 별도 공간 데이터 마트를 구축한다.

우선 수집 대상:

- 행정동/법정동 경계
- 도로명주소 전자지도
- 건물/주소 포인트
- 도로 구간
- 주정차 금지구역
- 공영주차장
- 공원/녹지
- CCTV, 보안등, 가로등
- 하천/소하천/배수로/상하수도 관련 시설

현재 개발 범위에서는 SGIS 경계, `202605_건물DB_전체분(주소정보)`, 공공데이터포털의 도시공원·주차장·CCTV·주정차금지구역 데이터만 사용한다. 처리는 PostgreSQL 공간 데이터 마트 기준으로 구현하고, LLM은 좌표를 생성하지 않고 DB의 위치 후보 ID와 행정구역 판단 결과만 사용한다.

### 7. 고시공고

1차 범위에서는 제외한다.

제외 사유:

- 공시송달, 운행정지명령 반송, 채용, 입찰, 보조금, 체납 등 검색 오염 가능성이 큼
- 개별 대상자·기간 한정 문서가 많아 법적 근거로 쓰기 위험함
- 선별/OCR/개인정보 제거 비용이 현재 우선순위보다 큼

추후 주정차 금지구역, 도로 통행제한, 단수, 안전통제처럼 현재 행정상태를 바꾸는 고시만 `LOCAL_NOTICE`로 별도 수집한다.

### 8. AIHub 데이터

이미 확보한 데이터:

- 행정법 LLM 사전학습 및 Instruction Tuning 데이터
- 공공 민원 상담 LLM 사전학습 및 Instruction Tuning 데이터
- 문서 이해 기반 시각요소 생성 데이터

용도는 학습/튜닝/문체/문서 이해 보조다. 법적 근거, 민원 수용 여부, 최종 승인 판단에는 사용하지 않는다.

## 남은 우선순위

1. 국가법령정보 API 적재와 조항 검증
2. SGIS 경계와 건물DB 주소 포인트 적재
3. 공공데이터포털 공원/주차장/CCTV/주정차금지구역 적재
4. 아산시 조직도 적재와 부서 추천 규칙 생성
5. 민원편람 `PROCEDURE` 적재
6. 새올 공개 상담민원 비식별 이력 적재
7. AIHub 데이터는 운영 근거가 아니라 평가/튜닝 후보로 분리

## 완료 기준

- 자동 발송 0건
- 자동 완료 0건
- 승인 없는 완료 0건
- 근거 없는 법적 주장 0건
- 잘못된 시행일/관할 근거 0건
- PII 외부 유출 0건
- 프롬프트 인젝션 정책 우회 0건
- 골든 데이터 기준 `Recall@10 >= 0.95`
- 초안 주장 근거 연결률 `100%`
- 담당 부서 추천 `Top-3 >= 0.95`
