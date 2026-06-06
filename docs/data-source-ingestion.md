# 데이터 수집과 적재 정책

현재 개발 기준의 최종 데이터 범위는 [final-data-scope.md](final-data-scope.md)를 우선한다. DB 구성과 파일 배치 경로는 [current-db-and-file-layout.md](current-db-and-file-layout.md)를 따른다. API 키는 `SGIS`, 공공데이터포털, 국가법령정보 단위로 관리하고, 세부 수집 대상은 해당 문서에 정의된 데이터만 사용한다.

일시적인 API 응답을 직접 근거로 사용하지 않는다. API 응답은 먼저 DB에 적재하고, 출처 메타데이터·해시·버전·검증 상태·법적 근거 허용 여부를 저장한 뒤 검색과 검증에 사용한다.

## 이미 연결된 API

| 데이터 | 상태 | 저장 목적 | 법적 근거 |
| --- | --- | --- | --- |
| 국가법령정보 공동활용 Open API | 구현됨 | `OFFICIAL_LAW` | 가능. 단 `NATIONAL`, `VERIFIED_OFFICIAL`, 시행일/조항/출처 검증 통과 필요 |
| 국민권익위원회 민원정책 질의응답조회서비스 | 구현됨 | `PROCEDURE` | 불가 |
| 국민권익위원회 민원빅데이터 분석정보 API 2022~2025 | 구현됨 | `HISTORICAL_CASE` | 불가 |
| 민원사무서식 Open API | 연결 대상 | `PROCEDURE` | 불가 |

## 확보한 AIHub 데이터

| 데이터 | 사용 |
| --- | --- |
| 행정법 LLM 사전학습 및 Instruction Tuning 데이터 | 학습/튜닝 후보, 법적 근거 금지 |
| 공공 민원 상담 LLM 사전학습 및 Instruction Tuning 데이터 | 문체/상담 패턴 참고, 법적 근거 금지 |
| 문서 이해 기반 시각요소 생성 데이터 | 문서/첨부 이해 보조, 법적 근거 금지 |

AIHub 데이터는 모델 개선용 자료이며 민원 수용 여부, 법령 사실 기억, 최종 승인 판단에는 사용하지 않는다.

## 국가법령

- 스크립트: `ai-rag-engine/sync_official_sources.py`
- API:
  - `https://www.law.go.kr/DRF/lawSearch.do`
  - `https://www.law.go.kr/DRF/lawService.do`
- 키: `LAW_API_OC`
- 저장:
  - `source_registry`
  - `legal_document_versions`
  - `legal_provisions`
  - `knowledge_documents`
  - `knowledge_purpose`

법적 근거 허용 조건:

- 출처가 국가법령정보 공동활용 API
- 관할 `NATIONAL`
- 조항 단위 본문 존재
- 시행일이 처리일 기준 유효
- 출처 최신성 유효
- `verification_status=VERIFIED_OFFICIAL`
- `legal_evidence_allowed=true`

## 아산시 자치법규

- 스크립트: `ai-rag-engine/sync_local_ordinances.py`
- API: 국가법령정보 공동활용 Open API `target=ordin`
- 키: `LAW_API_OC`
- 관할: `ASAN`
- 기본 검증 상태: `VERIFIED_INTERNAL`
- 기본 법적 근거 허용: `false`

저장 필드:

- 자치법규명
- 자치법규 ID
- 조항 키
- 조항 본문
- 관할 코드
- 공포일
- 시행일
- 원문 URL
- 원문 해시
- 출처 버전

자치법규는 공식 출처지만 폐지/개정/시행기간/관할 검증 정책이 국가법령과 다르다. 별도 정책을 구현하기 전에는 절차/검토 참고 자료로만 사용한다.

실행:

```powershell
cd ai-rag-engine
$env:ASAN_ORDINANCE_SYNC_ENABLED = "true"
python sync_local_ordinances.py
```

## 민원정책 Q&A

- 스크립트: `ai-rag-engine/sync_auxiliary_sources.py`
- API base: `https://apis.data.go.kr/1140100/CivilPolicyQnaService`
- endpoint: `/PolicyQnaList`
- 키:
  - `POLICY_QNA_API_KEY`
  - fallback: `COMPLAINT_BIGDATA_API_KEY` 또는 `COMPLAINT_BIGDATA_SERVICE_KEY`
- 목적: `PROCEDURE`
- 법적 근거: 금지

## 민원빅데이터 API

- 스크립트: `ai-rag-engine/sync_auxiliary_sources.py`
- API base: `https://apis.data.go.kr/1140100/minAnalsInfoView5`
- 키:
  - `COMPLAINT_BIGDATA_API_KEY`
  - `COMPLAINT_BIGDATA_SERVICE_KEY`
- 목적: `HISTORICAL_CASE`
- 법적 근거: 금지

연결된 주요 기능:

- 상승 키워드
- 상위 키워드
- 분류
- 통계
- 키워드 추이
- 유사 사례
- 연관어
- 오늘의 주제
- 기관 순위
- 지역 순위
- 키워드 건수
- 인구 대비
- 문서 빈도 상위 키워드
- 분석 리포트
- 성별/연령 통계

## 민원사무서식 Open API

현재 프로젝트에서 절차 참고 자료로 사용할 대상이다.

저장 목적:

- 민원 사무명
- 구비서류
- 처리기간
- 신청 방법
- 담당 부서
- 서식 URL

법적 근거로 쓰지 않는다. `PROCEDURE`, `legal_evidence_allowed=false`로 저장한다.

## 아산시 민원편람/민원사무편람

아산시 내부 SOP 공개본을 찾기 어렵기 때문에 공개 민원편람을 수집 대상으로 삼는다.

수집 대상:

- 아산시 민원편람 게시판
- 부서별 `.hwpx`, `.pdf`, `.hwp` 민원편람
- 차량민원 등 분야별 민원 안내 페이지

저장 목적:

- `PROCEDURE`
- 처리기간, 접수/처리부서, 구비서류, 관련 법령명, 신청 절차

법적 근거로 쓰지 않는다.

## 아산시 조직도와 업무분장

사용자가 별도 수집한다. 수집 후 다음 테이블에 적재한다.

- `organization_units`
- `assignment_rules`

필수 필드:

- 부서 코드
- 부서명
- 팀명
- 담당 업무
- 대표 연락처
- 관할 코드
- 유효 시작일/종료일
- 조직개편 이력
- 민원 유형별 배정 규칙

## 새올 전자민원창구 이력

수집 범위는 2021년 이후로 제한한다.

사용 조건:

- 원문 개인정보 제거
- 비식별 처리 결과만 저장
- 보조 사용 승인 필요
- 담당 부서 추천과 문체 참고에만 사용
- 법적 근거 금지

저장 대상:

- `historical_complaints`

필수 필드:

- 외부 ID
- 비식별 제목/내용
- 접수일
- 최종 담당 부서
- 처리 결과 요약
- 처리 기간
- 위치 텍스트 또는 읍면동
- 재분류/이송 여부
- 콘텐츠 해시

## GIS/공간 데이터

현재 개발 범위의 GIS 데이터는 SGIS 경계, `202605_건물DB_전체분(주소정보)`, 공공데이터포털 전국도시공원정보표준데이터, 전국주차장정보표준데이터, 행정안전부 CCTV정보 조회서비스, 전국주정차금지(지정)구역표준데이터로 제한한다. 아직 확보하지 않은 내부 GIS/도로관리/상하수도/축산/공동주택 별도 대장은 제외한다.

위치 후보 검증을 위해 별도 공간 데이터 마트를 만든다.

우선 수집 대상:

- 행정동/법정동 경계
- 도로명주소 전자지도
- 주소 포인트/건물
- 도로 구간
- 주정차 금지구역
- 공영주차장
- 공원/녹지
- CCTV, 보안등, 가로등
- 하천/소하천/배수로/상하수도 시설

권장 출처:

- 주소기반산업지원서비스/주소정보누리집
- 국가공간정보포털
- SGIS
- 공공데이터포털 표준데이터
- 아산시 공공데이터
- 충남 공간정보 포털

처리 원칙:

- LLM은 좌표를 생성하지 않는다.
- DB/GIS가 위치 후보를 만들고 사람 확인 전에는 `NEEDS_LOCATION`을 유지한다.
- 위치 후보는 `location_candidates`와 향후 공간 테이블에 저장한다.

## 아산시 고시공고

1차 범위에서는 제외한다.

제외 이유:

- 공시송달, 운행정지명령 반송, 채용, 입찰, 보조금, 체납 등 검색 오염 가능성이 큰 문서가 많다.
- 개별처분/대상자 문서는 RAG 근거로 부적합하다.
- 첨부파일 OCR, 개인정보 제거, 적용기간 추출 비용이 크다.

추후 선별 수집 후보:

- 주정차 금지구역 지정/변경/해제
- 도로 통행제한
- 도로점용/굴착
- 단수/하수도 공사
- 안전통제
- 공원/하천/시설물 이용 제한

이 경우에도 `LOCAL_NOTICE`, `legal_evidence_allowed=false`로 시작한다.

## 실행 예시

```powershell
cd ai-rag-engine
Copy-Item .env.example .env
```

국가법령:

```powershell
$env:OFFICIAL_SOURCE_SYNC_ENABLED = "true"
python sync_official_sources.py
```

아산시 자치법규:

```powershell
$env:ASAN_ORDINANCE_SYNC_ENABLED = "true"
python sync_local_ordinances.py
```

보조 API:

```powershell
$env:AUXILIARY_SOURCE_SYNC_ENABLED = "true"
$env:COMPLAINT_BIGDATA_API_SYNC_ENABLED = "true"
$env:POLICY_QNA_API_SYNC_ENABLED = "true"
python sync_auxiliary_sources.py
```

## 원칙

API 키와 수집 코드는 DB에 저장하지 않는다. DB에는 수집된 데이터, 출처 메타데이터, 해시, 버전, 검증 상태, 법적 근거 허용 여부만 저장한다.
## Current Implementation Snapshot

This section is authoritative when terminal rendering of Korean text is broken.

### Loaded API Sources

| Source | Loader | Current result | Legal evidence |
| --- | --- | ---: | --- |
| National Law API | `sync_official_sources.py` | 131 national law documents loaded | Yes, only when `NATIONAL`, `VERIFIED_OFFICIAL`, valid effective date, and provision text exist |
| National Law API, Asan ordinances | `sync_local_ordinances.py` | 105 Asan ordinance reference documents loaded | No for current scope |
| Anti-Corruption and Civil Rights Commission complaint big data 2022-2025 | `sync_auxiliary_sources.py` | Included in 34 auxiliary documents | No |
| Anti-Corruption and Civil Rights Commission policy Q&A | `sync_auxiliary_sources.py` | 0 records for the current query set | No |
| Chungnam civil complaint form/procedure API | `sync_minwon_forms.py` | 360 procedure records loaded | No |
| data.go.kr spatial APIs | `fetch_spatial_api_sources.py`, `sync_spatial_sources.py` | Parks 121, parking lots 43, CCTV 500, parking restrictions 249 | No |

### Loaded Downloaded Sources

| Source | Loader | Current result | Use |
| --- | --- | ---: | --- |
| Chungnam building address DB, filtered to Asan | `convert_chungnam_building_db.py`, `sync_spatial_sources.py` | 70,533 address points | Location candidate generation and jurisdiction support |
| 2018-2026 Asan civil complaint manuals | `sync_local_file_data_mart.py` | Included in local file data mart | Procedure reference only |
| Current Asan ordinance list | `sync_local_file_data_mart.py` | 1 reference record | Reference/department confirmation only |
| Saeol public complaints from 2021 onward | `sync_local_file_data_mart.py` | 1 source file record | Historical/style/routing support only |
| Asan city organization chart | `sync_organization_chart.py` | 110 parsed rows, 34 units, 110 assignment rules | Routing support with human confirmation only |
| AIHub document visual dataset | `sync_local_file_data_mart.py` | 42 file records | Evaluation/training candidate only |
| AIHub public complaint LLM dataset | `sync_local_file_data_mart.py` | 24 file records | Style/training candidate only |
| AIHub administrative law LLM dataset | `sync_local_file_data_mart.py` | 22 file records | Evaluation/training candidate only |

### Current DB Counts

| Table or group | Rows |
| --- | ---: |
| `source_registry` | 11 |
| `knowledge_documents` | 842 |
| `legal_document_versions` | 236 |
| `legal_provisions` | 11,330 |
| `data_mart_raw_records` | 658 |
| `data_mart_normalized_records` | 559 |
| Actual Asan `organization_units` | 34 |
| Actual Asan `assignment_rules` | 110 |
| `spatial_address_points` | 70,533 |
| `spatial_facilities` CCTV | 500 |
| `spatial_facilities` PARK | 121 |
| `spatial_facilities` PARKING_LOT | 43 |
| `spatial_parking_restrictions` | 249 |
| `spatial_admin_boundaries` | 17 |

### Current Judge Metrics

| Metric | Value |
| --- | ---: |
| Classification accuracy | 1.0000 |
| Department Top-3 | 1.0000 |
| Blocker accuracy | 1.0000 |
| Claim evidence coverage | 1.0000 |
| Evidence title relevance | 1.0000 |
| Template completeness | 1.0000 |
| Safety failures | 0 |
| Data readiness score | 1.0000 |

The evidence check now verifies expected official law-title relevance, not only
the existence of a citation ID.

Completion audit outputs:

- `ai-rag-engine/data/evaluation/completion_audit.latest.json`
- `ai-rag-engine/data/evaluation/completion_audit.latest.md`

Current completion audit status is `PASS`: loaded data, SGIS boundaries, worker
DB authentication, HWP raw retention, HWP searchable quality gate, and safety
gates pass.

### Remaining External Inputs

- SGIS administrative boundary data is loaded from `ai-rag-engine/data/spatial/asan_admin_boundaries.geojson` after SGIS API retrieval and coordinate conversion to WGS84.
- `WORKER_DB_USER` and `WORKER_DB_PASSWORD` now authenticate successfully against the local DB.
- Binary `.hwp` manuals are extracted with `WORKER_HWP_TEXT_COMMAND`; all 142 files remain in raw records, 44 meaningful extractions are searchable, and 98 low-quality table-placeholder extractions are blocked from retrieval and logged in `data_mart_load_errors`.
- `asan_city_organization.docx` is loaded as routing support: 110 parsed duty/contact rows, 34 actual Asan organization units, and 110 assignment rules. It is not legal evidence and does not authorize automatic final assignment.
