# Current DB And File Layout

This document is the synchronized map of the current database schema, ingestion
inputs, and project file layout. It follows the final scope in
[final-data-scope.md](final-data-scope.md).

## Database State

Authoritative database migrations are in:

```text
egov-boot-web/src/main/resources/db/migration
```

Current Flyway version:

```text
V18
```

Operational table groups:

| Area | Tables | Purpose |
| --- | --- | --- |
| Complaint workflow | `complaints`, `complaint_issues`, `department_tasks`, `location_candidates` | Complaint intake, issue decomposition, task routing, human-confirmed location candidates |
| Sensitive payloads | `complaint_sensitive_payloads`, `complaint_attachments`, `attachment_analysis` | Restricted raw content, attachment quarantine, approved derived data |
| Jobs and worker coordination | `processing_jobs` | Spring-owned job queue and worker leases |
| Legal/source knowledge | `source_registry`, `legal_document_versions`, `legal_provisions`, `legal_relations`, `knowledge_documents`, `knowledge_purpose` | Official source registry, legal document versions, searchable governed knowledge |
| Retrieval and evidence | `retrieval_runs`, `evidence_snapshots` | Immutable evidence copies used during draft generation |
| Draft and verification | `official_drafts`, `draft_claims`, `claim_evidence_links`, `verification_results`, `human_reviews` | Claim-level draft structure, evidence links, deterministic checks, human review |
| Audit and idempotency | `audit_logs`, `workflow_audit_events`, `idempotency_records` | API audit, workflow audit, duplicate write protection |
| Data mart | `data_mart_ingestion_runs`, `data_mart_raw_records`, `data_mart_normalized_records`, `data_mart_load_errors` | Raw/normalized ingestion records and load errors |
| Spatial data mart | `spatial_source_registry`, `spatial_admin_boundaries`, `spatial_address_points`, `spatial_road_segments`, `spatial_facilities`, `spatial_parking_restrictions`, `spatial_location_resolution_runs`, `spatial_location_candidates` | GIS source registry, boundaries, address/facility layers, resolver runs and candidates |

Legal evidence rule:

- Allowed: verified national law provisions from the National Law API.
- Not allowed: SGIS, data.go.kr spatial data, civil complaint Q&A, civil complaint big data, Saeol history, AIHub datasets, manuals, organization chart, downloaded ordinance list.
- Asan ordinances are collected with `LAW_API_OC`, but for the current scope they are reference/department-confirmation data only.

## Project File Layout

```text
Unstructured/
  egov-boot-web/
    src/main/resources/db/migration/     Flyway migrations V1~V18
    src/main/java/.../complaint/         authoritative Spring workflow/API/domain
    src/main/resources/static/dashboard/ reviewer dashboard
  ai-rag-engine/
    data/API/                            API clients
    data/spatial/                        SGIS, building DB, data.go.kr spatial CSV/GeoJSON files
    data/minwon_manuals/                 2018~2026 Asan civil complaint manuals
    data/local_ordinances/               downloaded current ordinance list metadata
    data/saeol/                          de-identified 2021+ Saeol public consultation data
    data/organization/                   Asan city organization/work assignment data
    data/aihub/document_visual/          AIHub document visual dataset
    data/aihub/public_complaint_llm/     AIHub public civil complaint LLM dataset
    data/aihub/administrative_law_llm/   AIHub administrative law LLM dataset
    data/evaluation/                     golden/prediction evaluation files
    sync_official_sources.py             National law ingestion
    sync_local_ordinances.py             Asan ordinance reference ingestion
    sync_auxiliary_sources.py            Q&A and complaint big-data ingestion
    fetch_spatial_api_sources.py         data.go.kr spatial API downloader
    convert_chungnam_building_db.py      Chungnam building DB to Asan CSV converter
    sync_spatial_sources.py              GIS/spatial mart ingestion
    spatial_location_resolver.py         GIS-backed location candidate resolver
  docs/
    final-data-scope.md                  final data source and usage rules
    current-db-and-file-layout.md        this synchronized DB/file layout
    gis-spatial-data-mart.md             spatial mart details
    data-source-ingestion.md             ingestion policy
    db-schema-review.md                  DB schema review
```

## Where To Put Downloaded Data

Put downloaded files under `ai-rag-engine/data/` using the exact paths below.
If a source arrives as multiple files, keep them in the listed directory and set
the `.env` variable to the main normalized CSV/XLSX file or directory.

| Downloaded dataset | Target path | `.env` variable | DB target | Use |
| --- | --- | --- | --- | --- |
| `202605_건물DB_전체분(주소정보)` | `ai-rag-engine/data/spatial/asan_address_points.csv` | `BUILDING_DB_ADDRESS_CSV`, `SPATIAL_ADDRESS_POINTS_CSV` | `spatial_address_points` | Address/building location candidates and jurisdiction checks |
| SGIS boundary download, if downloaded as file | `ai-rag-engine/data/spatial/asan_admin_boundaries.geojson` | `SPATIAL_ADMIN_BOUNDARIES_GEOJSON` | `spatial_admin_boundaries` | Asan/eupmyeondong/legal-dong boundary matching |
| 전국도시공원정보표준데이터 export | `ai-rag-engine/data/spatial/asan_parks.csv` | `SPATIAL_PARKS_CSV` | `spatial_facilities` with `facility_type=PARK` | Park/green-space context |
| 전국주차장정보표준데이터 export | `ai-rag-engine/data/spatial/asan_parking_lots.csv` | `SPATIAL_PARKING_LOTS_CSV` | `spatial_facilities` with `facility_type=PARKING_LOT` | Parking lot context |
| 행정안전부 CCTV정보 조회서비스 export | `ai-rag-engine/data/spatial/asan_cctv.csv` | `SPATIAL_CCTV_CSV` | `spatial_facilities` with `facility_type=CCTV` | Safety/CCTV context |
| 전국주정차금지(지정)구역표준데이터 export | `ai-rag-engine/data/spatial/asan_parking_restrictions.csv` | `SPATIAL_PARKING_RESTRICTIONS_CSV` | `spatial_parking_restrictions` | Illegal parking/restriction-zone context |
| `2018~2026 민원편람` | `ai-rag-engine/data/minwon_manuals/` | `MINWON_MANUAL_DIR` | `knowledge_documents`, `knowledge_purpose`, data mart tables | Procedure guidance only |
| `현행 자치법규 리스트` | `ai-rag-engine/data/local_ordinances/asan_current_ordinances.xlsx` | `ASAN_CURRENT_ORDINANCE_LIST_FILE` | source metadata/reference records | Reference and department confirmation only |
| `아산시 새올전자민원창구 공개 상담민원` | `ai-rag-engine/data/saeol/asan_saeol_public_complaints_2021_2026.xlsx` | `SAEOL_PUBLIC_COMPLAINTS_FILE` | `historical_complaints` | Department routing and style reference after de-identification |
| `아산시청 조직도` | `ai-rag-engine/data/organization/asan_city_organization.xlsx` | `ASAN_ORGANIZATION_FILE` | `organization_units`, `assignment_rules` | Department candidate generation |
| `문서 이해 기반 시각요소 생성 데이터` | `ai-rag-engine/data/aihub/document_visual/` | `AIHUB_DOCUMENT_VISUAL_DIR` | training/evaluation area only | Document understanding experiments |
| `공공 민원 상담 LLM 사전학습 및 Instruction Tuning 데이터` | `ai-rag-engine/data/aihub/public_complaint_llm/` | `AIHUB_PUBLIC_COMPLAINT_LLM_DIR` | training/evaluation area or restricted `STYLE_REFERENCE` | Style/tuning candidate |
| `행정법 LLM 사전학습 및 Instruction Tuning 데이터` | `ai-rag-engine/data/aihub/administrative_law_llm/` | `AIHUB_ADMINISTRATIVE_LAW_LLM_DIR` | training/evaluation area only | Prompt/tuning candidate |

## API Data With No Download File Required

| API key group | Loader | DB target | Notes |
| --- | --- | --- | --- |
| National Law API, `LAW_API_OC` | `sync_official_sources.py` | `source_registry`, `legal_document_versions`, `legal_provisions`, `knowledge_documents`, `knowledge_purpose` | Verified national law can become legal evidence |
| National Law API, `LAW_API_OC` for Asan ordinances | `sync_local_ordinances.py` | same legal/source tables | Current scope: reference/department confirmation only |
| data.go.kr Q&A | `sync_auxiliary_sources.py` | `knowledge_documents`, `knowledge_purpose`, data mart tables | `PROCEDURE`, not legal evidence |
| data.go.kr complaint big data 2022~2025 | `sync_auxiliary_sources.py` | `knowledge_documents`, `knowledge_purpose`, data mart tables | `HISTORICAL_CASE`, not legal evidence |
| SGIS direct API URL | `sync_spatial_sources.py` | `spatial_admin_boundaries` | Only when `SPATIAL_ADMIN_BOUNDARIES_GEOJSON` points to SGIS API URL |
| data.go.kr spatial standard APIs | `fetch_spatial_api_sources.py`, then `sync_spatial_sources.py` | `spatial_facilities`, `spatial_parking_restrictions` | Writes normalized CSV files under `ai-rag-engine/data/spatial/` before DB load |

## Current Spatial Load Snapshot

Last verified local load:

| Dataset | File | Loaded rows |
| --- | --- | ---: |
| Chungnam building DB filtered to Asan | `ai-rag-engine/data/spatial/asan_address_points.csv` | 70,533 |
| National city park standard data | `ai-rag-engine/data/spatial/asan_parks.csv` | 121 |
| National parking lot standard data | `ai-rag-engine/data/spatial/asan_parking_lots.csv` | 43 |
| Ministry of the Interior and Safety CCTV information | `ai-rag-engine/data/spatial/asan_cctv.csv` | 500 |
| National parking/standing restriction-zone standard data | `ai-rag-engine/data/spatial/asan_parking_restrictions.csv` | 249 |
| SGIS boundary file | `ai-rag-engine/data/spatial/asan_admin_boundaries.geojson` | not loaded yet; SGIS credentials/API URL are not configured |

## Excluded For Current Scope

Do not place these into the project for the current implementation:

- broad Asan notices/announcements
- internal road management ledger
- internal water/sewer/manhole/drainage GIS ledgers
- livestock permit ledgers
- apartment/public housing management ledgers
- road safety signs, VMS, streetlight/security-light datasets not yet acquired

The schema has room for some of these future sources, but the current ingestion
scope excludes them to avoid search contamination and personal-data risk.
