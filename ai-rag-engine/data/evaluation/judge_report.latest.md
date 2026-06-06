# Judge Report - Trust Template Pipeline

Generated at: 2026-06-06T09:13:24.372087+00:00

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
- Raw data-mart records: 657
- Normalized data-mart records: 514
- Address points loaded: 70533
- CCTV facilities loaded: 500
- Parking restrictions loaded: 249
- SGIS/admin boundaries loaded: 0
- Data readiness score: 0.8333
- Data readiness checks: `{"addressPointsLoaded": true, "dataMartLoaded": true, "nonEvidenceSourcesQuarantined": true, "officialLawPresent": true, "sgisBoundariesLoaded": false, "spatialFacilitiesLoaded": true}`
- Knowledge by purpose: `{"EVALUATION_TRAINING": 64, "HISTORICAL_CASE": 34, "LOCAL_ORDINANCE_REFERENCE": 1, "OFFICIAL_LAW": 236, "PROCEDURE": 425, "STYLE_REFERENCE": 24, "UNVERIFIED_LEGACY": 13}`

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
5. Remaining weaknesses: SGIS boundary layer is missing, department organization
   data is intentionally deferred, HWP binary manuals are metadata-only until a
   trusted extractor is configured, and policy Q&A returned no records for the
   current query set.

## Complaint Type Distribution

`{"ENVIRONMENT": 10, "GENERAL": 80, "HAZARDOUS_MATERIAL": 10, "ILLEGAL_DUMPING": 10, "ILLEGAL_PARKING": 10, "NOISE": 10, "ROAD_DAMAGE": 10, "TRAFFIC_SIGN": 10}`
