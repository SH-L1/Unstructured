# 제한된 Python 지원 작업자

Python은 권위 있는 민원 상태를 소유하지 않는다. 모든 AI/수집/OCR/검색 작업은 Spring 내부 작업 API에서 임대를 받고 완료 또는 실패를 보고한다.

## 역할

Python이 수행하는 작업:

- `REDACT`
- `EXTRACT_ATTACHMENT`
- `RETRIEVE`
- `VERIFY`
- `CLASSIFY_ISSUES`
- `DRAFT`
- 공식/보조 데이터 수집
- OpenSearch 파생 인덱스 동기화
- 평가 스크립트 실행

Python이 수행하지 않는 작업:

- 민원 승인
- 민원 완료
- 검토자/승인자 결정 대체
- 외부 민원 시스템 발송
- 미검증 문서를 법적 근거로 승격
- LLM 좌표/GeoJSON 생성

## 실행

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
.venv\Scripts\python.exe worker.py
```

`WORKER_SERVICE_TOKEN`은 Spring과 같은 값을 사용한다. 최소 32자 이상으로 설정한다.

## DB 접속

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
WORKER_DB_USER
WORKER_DB_PASSWORD
```

`WORKER_DB_USER`는 수집/지원 작업용 제한 DB 계정이다. 운영에서는 애플리케이션 계정을 재사용하지 않는다.

## AI 공급자

```text
AI_PROVIDER=mock
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4o-mini
OPENAI_BASE_URL=https://api.openai.com/v1
```

`mock`, `openai`, `bedrock` 중 하나를 사용한다. 외부 공급자는 공통 runtime에서 timeout, bounded retry, 비용 한도, 회로 차단을 적용한다.

## 첨부 파이프라인

첨부는 원본 격리 저장소에서 읽고 다음 검사를 통과해야 한다.

- 크기 제한
- 확장자 검사
- 매직바이트 검사
- 악성 파일 검사
- EXIF/민감정보 제거
- OCR/PDF/HWP/HWPX 추출
- 비식별 파생본 생성

필요 명령:

```text
WORKER_MALWARE_SCAN_COMMAND
WORKER_OCR_COMMAND
WORKER_PDF_TEXT_COMMAND
WORKER_HWP_TEXT_COMMAND
```

도구가 없거나 실패하면 `approved_for_ai=false`로 차단한다.

## 공식 출처 수집

### 국가법령

```powershell
$env:OFFICIAL_SOURCE_SYNC_ENABLED = "true"
.venv\Scripts\python.exe sync_official_sources.py
```

국가법령은 `NATIONAL`, `VERIFIED_OFFICIAL`, `legal_evidence_allowed=true` 조건을 충족할 때만 법적 근거로 사용할 수 있다.

### 아산시 자치법규

```powershell
$env:ASAN_ORDINANCE_SYNC_ENABLED = "true"
.venv\Scripts\python.exe sync_local_ordinances.py
```

자치법규는 `ASAN` 관할, 공포일, 시행일, 조항 본문을 보존한다. 기본적으로 `legal_evidence_allowed=false`이며, 별도 검증 정책 전에는 자동 법적 근거로 쓰지 않는다.

## 보조 API 수집

```powershell
$env:AUXILIARY_SOURCE_SYNC_ENABLED = "true"
$env:COMPLAINT_BIGDATA_API_SYNC_ENABLED = "true"
$env:POLICY_QNA_API_SYNC_ENABLED = "true"
.venv\Scripts\python.exe sync_auxiliary_sources.py
```

보조 API:

- 국민권익위원회 민원정책 질의응답조회서비스
- 국민권익위원회 민원빅데이터 분석정보 API 2022~2025

저장 목적:

- Q&A: `PROCEDURE`
- 민원빅데이터: `HISTORICAL_CASE`

법적 근거로 쓰지 않는다.

## 직접 수집 데이터

### 새올 이력

2021년 이후 비식별 데이터만 사용한다. `DEPARTMENT_HISTORY_XLSX`로 지정하고 `DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE=true`일 때만 읽는다.

용도:

- 담당 부서 추천
- 문체 참고

법적 근거 금지.

### 아산시 민원편람

공개 민원편람/민원사무편람은 `PROCEDURE` 자료로 적재한다.

### 고시공고

1차 범위에서는 제외한다. 검색 오염 위험이 높고 개별처분 문서가 많기 때문이다.

## OpenSearch

```powershell
.venv\Scripts\python.exe sync_opensearch_indices.py
```

OpenSearch는 목적별 파생 검색 인덱스다. 권위는 PostgreSQL DB에 있다. 삭제/폐기/STALE 상태는 DB 기준으로 반영한다.

## 평가

```powershell
.venv\Scripts\python.exe -m unittest test_sync_official_sources.py test_sync_local_ordinances.py test_sync_auxiliary_sources.py test_sync_opensearch_indices.py test_worker.py test_provider_runtime.py test_knowledge_maintenance.py
.venv\Scripts\python.exe evaluate_golden.py data/evaluation/golden_cases.full.json data/evaluation/predictions.full.json --require-full
```

현재 합성 골든 데이터는 실제 개인정보를 포함하지 않는다. 실제 아산시 평가셋은 새올 비식별 이력에서 별도로 분리해야 한다.

## Legacy 자료

기존 `data/knowledge` Markdown 실험용 RAG 파일은 삭제했다. 운영 지식은 API/수집기를 통해 DB에 적재된 문서만 사용한다. `UNVERIFIED_LEGACY` enum과 검증 로직은 과거 DB 행이나 외부 유입 자료를 차단하기 위한 안전장치로 유지한다.
## 최종 데이터 범위

현재 개발 기준의 최종 데이터 범위는 [../docs/final-data-scope.md](../docs/final-data-scope.md)를 우선한다. Python 수집기는 SGIS, 공공데이터포털, 국가법령정보 API와 다운로드 완료 데이터만 적재 대상으로 삼는다.
## Current Data Load And Evaluation

This section is authoritative when terminal rendering of Korean text is broken.

Run order for the current data scope:

```powershell
python sync_official_sources.py
python sync_local_ordinances.py
python sync_auxiliary_sources.py
python sync_minwon_forms.py
python sync_local_file_data_mart.py
python sync_spatial_sources.py
python run_trust_pipeline_evaluation.py
python run_completion_audit.py
```

Current verified DB state:

| Area | Rows |
| --- | ---: |
| National law documents | 131 |
| Asan ordinance reference documents | 105 |
| Chungnam civil complaint form/procedure records | 360 |
| `knowledge_documents` total | 797 |
| `legal_provisions` | 11,330 |
| `data_mart_raw_records` | 657 |
| `data_mart_normalized_records` | 514 |
| Asan address points | 70,533 |
| Parks | 121 |
| Parking lots | 43 |
| CCTV | 500 |
| Parking restriction zones | 249 |
| SGIS/admin boundaries | 0 |

Evaluation outputs:

- `data/evaluation/pipeline_predictions.latest.json`
- `data/evaluation/training_decision.latest.json`
- `data/evaluation/judge_report.latest.md`
- `data/evaluation/completion_audit.latest.json`
- `data/evaluation/completion_audit.latest.md`

Current evaluation status is `PASS_WITH_LIMITATIONS`. The deterministic synthetic
judge run passed classification, department Top-3, blocker, claim evidence
coverage, official law-title relevance, template completeness, and safety checks.
This is still not a production-quality claim until SGIS boundaries and HWP text
extraction are completed. Department organization data is excluded from the
current active scope by user request, and AIHub/local historical data remains
blocked from legal-evidence or fine-tuning use until privacy and label quality
are proven.
