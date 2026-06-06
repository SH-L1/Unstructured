# Judge Report - Trust Template Pipeline

Generated at: 2026-06-06T15:35:27.396944+00:00

## Verdict

Status: PASS_WITH_LIMITATIONS

This is a deterministic local pipeline and data-readiness evaluation. It is not a
production accuracy claim. The synthetic evaluation now checks not only whether
claims have evidence IDs, but whether the cited official law titles match the
expected complaint type.

## Metrics

- Case count: 150
- Classification accuracy: 1.0000
- Department Top-3: 1.0000
- Blocker accuracy: 1.0000
- Claim evidence coverage: 1.0000
- Evidence title relevance: 1.0000
- Template completeness: 1.0000
- Safety failures: 0

## DB Quality Gates

- Illegal legal-evidence rows: 0
- Legal provisions loaded: 11330
- Raw data-mart records: 658
- Normalized data-mart records: 559
- Address points loaded: 70533
- CCTV facilities loaded: 500
- Parking restrictions loaded: 249
- SGIS/admin boundaries loaded: 17
- Asan organization units loaded: 34
- Asan assignment rules loaded: 110
- Data readiness score: 1.0000
- Data readiness checks: `{"addressPointsLoaded": true, "asanOrganizationLoaded": true, "dataMartLoaded": true, "nonEvidenceSourcesQuarantined": true, "officialLawPresent": true, "sgisBoundariesLoaded": true, "spatialFacilitiesLoaded": true}`
- Knowledge by purpose: `{"EVALUATION_TRAINING": 64, "HISTORICAL_CASE": 34, "LOCAL_ORDINANCE_REFERENCE": 1, "OFFICIAL_LAW": 236, "ORGANIZATION_ROUTING": 1, "PROCEDURE": 469, "STYLE_REFERENCE": 24, "UNVERIFIED_LEGACY": 13}`

## Critical Assessment

1. Legal grounding is structurally valid only when evidence comes from
   `VERIFIED_OFFICIAL` national-law records. Local ordinances, manuals, Saeol
   history, spatial data, and AIHub data are blocked from legal-evidence use.
2. The previous shallow citation check was insufficient. It has been tightened
   with evidence-title relevance, so unrelated official law records no longer
   pass as valid support.
3. Accuracy metrics are measured on synthetic cases. They prove regression
   behavior and hard-gate behavior, not real Asan production performance.
4. Fine-tuning remains rejected: `NO_FINE_TUNING`. Current datasets do
   not prove privacy safety, label quality, and legal-fact suitability.
5. Remaining weaknesses: the provided organization chart is loaded only for the
   departments and duties present in `asan_city_organization.docx`; it is routing
   support, not an automatic final assignment authority. HWP binary manuals are
   raw-loaded but only meaningful extractions are promoted into searchable
   procedure knowledge, and policy Q&A returned no records for the current query
   set. These sources remain routing/procedure/style support only and cannot
   become legal evidence.

## Complaint Type Distribution

`{"ENVIRONMENT": 10, "GENERAL": 80, "HAZARDOUS_MATERIAL": 10, "ILLEGAL_DUMPING": 10, "ILLEGAL_PARKING": 10, "NOISE": 10, "ROAD_DAMAGE": 10, "TRAFFIC_SIGN": 10}`
