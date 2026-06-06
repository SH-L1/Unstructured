# GIS Spatial Data Mart

This document describes the GIS implementation added for Asan-wide complaint
handling. Spatial data is operational context only. It supports location
confirmation, jurisdiction checks, department routing, and reviewer field
context, but it is not legal evidence.

## Database

Flyway `V18__create_spatial_data_mart_tables.sql` adds:

- `spatial_source_registry`
- `spatial_admin_boundaries`
- `spatial_address_points`
- `spatial_road_segments`
- `spatial_facilities`
- `spatial_parking_restrictions`
- `spatial_location_resolution_runs`
- `spatial_location_candidates`

The schema is Postgres/Flyway managed and H2-compatible at the DDL level. It
stores latitude/longitude, bounding boxes, and GeoJSON payload text. Production
PostgreSQL can later add PostGIS geometry columns in a follow-up migration if
needed, but V18 does not require PostGIS to pass current tests.

## Ingestion

Loader:

```powershell
cd ai-rag-engine
$env:SPATIAL_PUBLIC_API_MAX_PAGES = "300"
python fetch_spatial_api_sources.py
python convert_chungnam_building_db.py
$env:SPATIAL_SYNC_ENABLED = "true"
python sync_spatial_sources.py
```

`fetch_spatial_api_sources.py` calls the configured data.go.kr APIs and writes
normalized Asan-only CSV files. `convert_chungnam_building_db.py` filters the
Chungnam building DB raw text file to Asan address records. `sync_spatial_sources.py`
then loads existing CSV/GeoJSON files into the spatial mart. Missing optional
files are skipped, so unavailable future sources do not block the current load.
Reloading a source replaces only rows belonging to that same source, so updated
files do not accumulate duplicates.

Credential handling:

- data.go.kr spatial APIs reuse `COMPLAINT_BIGDATA_API_KEY`,
  `COMPLAINT_BIGDATA_SERVICE_KEY`, or `POLICY_QNA_API_KEY` by default.
- `SPATIAL_DATA_GO_KR_SERVICE_KEY` is only an optional override when you want a
  different data.go.kr key for spatial APIs.
- SGIS uses separate credentials: `SGIS_CONSUMER_KEY` and
  `SGIS_CONSUMER_SECRET`. Leave `SGIS_ACCESS_TOKEN` empty for normal use; the
  ingestion helper issues and refreshes short-lived SGIS access tokens from the
  consumer credentials.
- SGIS credentials are needed only when `SPATIAL_*_GEOJSON` points directly to
  an SGIS API URL. If you downloaded the SGIS boundary file first, no SGIS key is
  needed at load time.
- Current local SGIS state: not loaded yet. `SGIS_CONSUMER_KEY`,
  `SGIS_CONSUMER_SECRET`, and a concrete `SGIS_ADMIN_BOUNDARY_URL` are required
  for direct API download. Do not include `accessToken` in the boundary URL; the
  code adds it and retries once after SGIS returns `errCd=-401`. Otherwise put
  the downloaded GeoJSON at `data/spatial/asan_admin_boundaries.geojson`.

## Phase 1 Data

Current implementation scope is limited to the data already confirmed in
`docs/final-data-scope.md`: SGIS boundaries, the 202605 building/address DB, and
data.go.kr parks, parking lots, CCTV, and parking restriction zones. Other
internal GIS ledgers remain excluded until separately acquired.

- `SPATIAL_ADMIN_BOUNDARIES_GEOJSON`: Asan administrative/legal-dong boundaries.
- `SPATIAL_ADDRESS_POINTS_CSV`: road-name address, jibun address, building and coordinate records.
- `SPATIAL_PARKING_LOTS_CSV`: public/private parking lot points.
- `SPATIAL_PARKING_RESTRICTIONS_CSV`: parking restriction and enforcement zones.
- `SPATIAL_PARKS_CSV`: parks, green areas, and public open-space facilities.

Verified local load counts:

| Source | Rows |
| --- | ---: |
| Asan building/address records from Chungnam building DB | 70,533 |
| Parks | 121 |
| Parking lots | 43 |
| Parking restriction zones | 249 |
| CCTV | 500 |

## Phase 2 Data

- `SPATIAL_CCTV_CSV`: CCTV and safety camera locations.
- `SPATIAL_STREETLIGHTS_CSV`: reserved for later use; excluded until data is acquired.
- `SPATIAL_ROAD_SIGNS_CSV`: reserved for later use; excluded until data is acquired.
- `SPATIAL_VMS_CSV`: reserved for later use; excluded until data is acquired.

## Phase 3 Data

Phase 3 sources are schema-ready but excluded from the current development
scope until data is acquired:

- `SPATIAL_LIVESTOCK_FARMS_CSV`
- `SPATIAL_PUBLIC_HOUSING_CSV`
- `SPATIAL_WATER_FACILITIES_CSV`
- `SPATIAL_SEWER_FACILITIES_CSV`
- `SPATIAL_ROAD_MANAGEMENT_CSV`

## Location Resolution

Resolver:

```powershell
cd ai-rag-engine
python spatial_location_resolver.py "아산시 배방읍 모산로 공영주차장 앞 불법주정차"
```

The resolver searches loaded GIS tables and records one
`spatial_location_resolution_runs` row plus zero or more
`spatial_location_candidates` rows. Even high-confidence matches require human
confirmation. If no candidate is found, the run is blocked with
`NEEDS_LOCATION`.

The LLM must not generate coordinates or GeoJSON. Coordinates must come from
loaded GIS data, or the location remains unresolved.

## External Data Sources To Prepare

Use official or government-operated sources only:

- Address data: Road Name Address Developer Center / address information distribution.
- Boundaries: national spatial information portal, SGIS, or official local GIS export.
- Parking, parks, CCTV, streetlights, signs, VMS: data.go.kr, Asan open data, Chungnam open data, or Asan department exports.
- Livestock, public housing, water/sewer, road ledger: Asan internal administrative GIS or responsible department ledgers. If no public dataset exists, request an official export from the department and load it as CSV.

Do not load broad Asan notices/announcements into this mart. They remain excluded
unless a future narrow pipeline selects only current administrative-state notices.
