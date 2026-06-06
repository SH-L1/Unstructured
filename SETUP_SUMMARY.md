# 설정 요약

## 서버

- 권위 서버: `egov-boot-web`
- Java: 17
- 빌드: Maven
- DB: PostgreSQL + Flyway V1~V18
- 로컬 포트: `8081`
- 검토 화면: `/dashboard`

## Spring 필수 환경 변수

운영/기본 프로필은 세션 인증과 민감정보 암호화 키를 요구한다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
SENSITIVE_DATA_KEY
INTAKE_PASSWORD
REVIEWER_PASSWORD
APPROVER_PASSWORD
KNOWLEDGE_ADMIN_PASSWORD
AUDITOR_PASSWORD
ADMIN_PASSWORD
WORKER_SERVICE_TOKEN
```

규칙:

- `SENSITIVE_DATA_KEY`는 최소 32자 이상이다.
- `WORKER_SERVICE_TOKEN`은 최소 32자 이상이며 Python 작업자와 Spring에 같은 값을 넣는다.
- 운영 인증은 서버 세션과 CSRF를 사용한다.
- 브라우저 API 키와 HTTP Basic API 인증은 사용하지 않는다.

## Python `.env`

Python 작업자와 수집기는 `ai-rag-engine/.env`를 사용한다.

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
WORKER_DB_USER
WORKER_DB_PASSWORD
WORKER_INTERNAL_BASE_URL
WORKER_SERVICE_TOKEN
```

`WORKER_DB_USER`는 수집기/보조 작업용 제한 DB 계정이다. 로컬 테스트에서는 임시로 `DB_USER`와 같은 값을 쓸 수 있지만 운영에서는 별도 role을 권장한다.

## AI 공급자

```text
AI_PROVIDER=mock
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4o-mini
OPENAI_BASE_URL=https://api.openai.com/v1
AWS_BEDROCK_REGION=ap-northeast-2
AWS_BEDROCK_MODEL_ID=
```

DB 적재나 로컬 검증만 할 때는 `AI_PROVIDER=mock`을 유지한다.

## 공공 API 키

```text
LAW_API_OC
COMPLAINT_BIGDATA_API_KEY
COMPLAINT_BIGDATA_SERVICE_KEY
POLICY_QNA_API_KEY
```

- `LAW_API_OC`: 국가법령정보 공동활용 Open API 키
- `COMPLAINT_BIGDATA_API_KEY`: 국민권익위원회 민원빅데이터 API serviceKey
- `COMPLAINT_BIGDATA_SERVICE_KEY`: 같은 키를 대체 이름으로 넣을 때 사용 가능
- `POLICY_QNA_API_KEY`: 국민권익위원회 민원정책 질의응답조회서비스 serviceKey

같은 공공데이터포털 serviceKey가 여러 서비스에 승인되어 있으면 `COMPLAINT_BIGDATA_API_KEY`와 `POLICY_QNA_API_KEY`에 같은 값을 넣을 수 있다.

## 수집 스위치

기본값은 모두 비활성이다.

```text
OFFICIAL_SOURCE_SYNC_ENABLED=false
ASAN_ORDINANCE_SYNC_ENABLED=false
AUXILIARY_SOURCE_SYNC_ENABLED=false
COMPLAINT_BIGDATA_API_SYNC_ENABLED=false
POLICY_QNA_API_SYNC_ENABLED=false
OPENSEARCH_SYNC_ENABLED=false
```

### 국가법령

```text
OFFICIAL_SOURCE_SYNC_ENABLED=true
OFFICIAL_SOURCE_INTERVAL_MINUTES=1440
OFFICIAL_SOURCE_SEARCH_DISPLAY=5
OFFICIAL_SOURCE_MAX_DOCUMENTS=200
OFFICIAL_LAW_QUERIES=민원 처리에 관한 법률,행정절차법,...
```

국가법령은 검증 후 `VERIFIED_OFFICIAL`, `NATIONAL`, `legal_evidence_allowed=true`로 사용할 수 있다.

### 아산시 자치법규

```text
ASAN_ORDINANCE_SYNC_ENABLED=true
ASAN_ORDINANCE_INTERVAL_MINUTES=1440
ASAN_ORDINANCE_JURISDICTION_CODE=ASAN
ASAN_ORDINANCE_SEARCH_DISPLAY=10
ASAN_ORDINANCE_MAX_DOCUMENTS=300
ASAN_ORDINANCE_SEARCH_QUERIES=아산시 민원,아산시 행정기구,...
```

자치법규는 시행일과 조항을 보존하지만 기본적으로 `legal_evidence_allowed=false`다.

### 보조 API

```text
AUXILIARY_SOURCE_SYNC_ENABLED=true
AUXILIARY_SOURCE_INTERVAL_MINUTES=1440
AUXILIARY_SOURCE_DISPLAY=5
AUXILIARY_SOURCE_QUERIES=아산시 민원,아산시 생활불편,...
COMPLAINT_BIGDATA_API_SYNC_ENABLED=true
POLICY_QNA_API_SYNC_ENABLED=true
```

보조 API 자료는 `PROCEDURE` 또는 `HISTORICAL_CASE`로 저장하고 법적 근거로 쓰지 않는다.

## 직접 수집 파일

```text
DEPARTMENT_HISTORY_XLSX=
DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE=false
```

새올 전자민원창구 이력이나 부서 배정 이력 XLSX를 넣을 때 사용한다.

현재 정책:

- 2021년 이후 이력만 사용
- 비식별 처리 필수
- `DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE=true`일 때만 읽음
- 법적 근거 금지

## 첨부 처리 도구

운영에서는 다음 도구 또는 동등한 대체 도구가 필요하다.

```text
WORKER_MALWARE_SCAN_COMMAND
WORKER_OCR_COMMAND
WORKER_PDF_TEXT_COMMAND
WORKER_HWP_TEXT_COMMAND
```

도구가 없거나 실패하면 첨부는 fail-closed로 차단한다.

## 고시공고

아산시 현재 고시공고는 1차 범위에서 제외한다. 일괄 적재하지 않는다.

추후 필요한 경우 다음처럼 별도 스위치를 추가한다.

```text
ASAN_NOTICE_SYNC_ENABLED=false
```

단, 이 파이프라인은 주정차 금지구역, 통행제한, 단수, 안전통제 등 현재 행정상태를 바꾸는 고시만 선별해야 한다.
## 최종 데이터/API 키 기준

최종 데이터 범위는 [docs/final-data-scope.md](docs/final-data-scope.md)를 따른다. API 키는 SGIS, 공공데이터포털, 국가법령정보 단위로 관리한다.

- `LAW_API_OC`: 국가법령정보. 국가법령 조항과 아산시 자치법규 수집에 사용한다.
- `COMPLAINT_BIGDATA_API_KEY`, `COMPLAINT_BIGDATA_SERVICE_KEY`: 공공데이터포털. 민원빅데이터와 공간 표준데이터에 사용한다.
- `POLICY_QNA_API_KEY`: 공공데이터포털. 민원정책 Q&A와 필요 시 공간 표준데이터 fallback에 사용한다.
- `SPATIAL_DATA_GO_KR_SERVICE_KEY`: 선택 override. 공공데이터포털 공간 API 키를 별도로 분리할 때만 사용한다.
- `SGIS_CONSUMER_KEY`, `SGIS_CONSUMER_SECRET`, `SGIS_ACCESS_TOKEN`: SGIS API URL을 직접 호출할 때 사용한다. SGIS 데이터를 파일로 내려받아 적재하면 적재 시점에는 필요 없다.
