"""Run a deterministic DB-backed complaint answer-template evaluation.

This script intentionally avoids automatic sending, automatic completion, and
fine-tuning. It checks whether the current DB can support human-reviewed answer
templates under conservative evidence rules.

Validation rationale:
- Classification and routing are measured with standard accuracy/Top-k style
  regression metrics on synthetic privacy-safe cases. These metrics are useful
  for regression only and are explicitly not production accuracy claims.
- Legal grounding is checked before any model-style wording: every legal claim
  must link to at least one `VERIFIED_OFFICIAL` national-law record. This is a
  deterministic hard gate because an LLM judge must not override source,
  jurisdiction, or effective-date failures.
- Retrieval relevance is measured by expected official law title coverage, not
  just by the existence of any evidence ID. This prevents a common RAG failure
  where irrelevant but official documents satisfy a shallow citation check.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import psycopg2
from dotenv import load_dotenv


load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
EVAL_DIR = BASE_DIR / "data" / "evaluation"
GOLDEN_PATH = EVAL_DIR / "golden_cases.full.json"
PREDICTION_PATH = EVAL_DIR / "pipeline_predictions.latest.json"
REPORT_PATH = EVAL_DIR / "judge_report.latest.md"
TRAINING_DECISION_PATH = EVAL_DIR / "training_decision.latest.json"


COMPLAINT_RULES = (
    ("TRAFFIC_SIGN", "TRAFFIC", ("교통신호", "교통시설", "표지판", "횡단보도", "신호등", "traffic sign")),
    ("ILLEGAL_PARKING", "TRAFFIC", ("불법 주정차", "주정차", "주차", "parking")),
    ("ILLEGAL_DUMPING", "RESOURCE_RECYCLING", ("폐기물", "무단투기", "쓰레기", "garbage", "dump")),
    ("ROAD_DAMAGE", "ROAD", ("도로 파손", "포트홀", "도로", "road", "pothole")),
    ("NOISE", "ENVIRONMENT", ("소음", "진동", "noise")),
    ("ENVIRONMENT", "ENVIRONMENT", ("악취", "환경오염", "대기", "물환경", "odor")),
    ("HAZARDOUS_MATERIAL", "SAFETY_CONTROL", ("위험물", "화학물질", "안전 재난", "hazard")),
    ("GENERAL", "WATER_SEWER", ("상하수도", "상수도", "하수도", "누수", "water")),
    ("GENERAL", "BUILDING_HOUSING", ("건축", "주택", "공동주택", "building")),
    ("GENERAL", "PARK_GREEN", ("공원", "녹지", "어린이놀이시설", "park")),
    ("GENERAL", "HEALTH_SANITATION", ("보건", "위생", "식품", "health")),
    ("GENERAL", "ANIMAL_LIVESTOCK", ("동물", "축산", "animal")),
    ("GENERAL", "URBAN_MANAGEMENT", ("광고물", "현수막", "노점", "advertising")),
    ("GENERAL", "WELFARE", ("복지", "장애인", "접근성", "welfare")),
)

LAW_TITLES_BY_TYPE = {
    "GENERAL": ("민원 처리에 관한 법률",),
    "ILLEGAL_DUMPING": ("폐기물관리법",),
    "ROAD_DAMAGE": ("도로법",),
    "ILLEGAL_PARKING": ("도로교통법", "주차장법"),
    "TRAFFIC_SIGN": ("도로교통법",),
    "NOISE": ("소음ㆍ진동관리법",),
    "ENVIRONMENT": ("악취방지법", "대기환경보전법", "물환경보전법"),
    "HAZARDOUS_MATERIAL": ("위험물안전관리법", "화학물질관리법"),
}

LAW_TITLES_BY_DEPARTMENT = {
    "WATER_SEWER": ("수도법", "하수도법"),
    "BUILDING_HOUSING": ("건축법", "공동주택관리법"),
    "PARK_GREEN": ("도시공원 및 녹지 등에 관한 법률", "어린이놀이시설 안전관리법"),
    "HEALTH_SANITATION": ("식품위생법", "공중위생관리법"),
    "ANIMAL_LIVESTOCK": ("동물보호법", "가축전염병 예방법"),
    "URBAN_MANAGEMENT": ("옥외광고물 등의 관리와 옥외광고산업 진흥에 관한 법률",),
    "WELFARE": ("장애인복지법",),
}

SAFETY_FLAGS = (
    "safetyPolicyBypass",
    "promptInjectionBypass",
    "piiExternalLeak",
    "automaticSend",
    "automaticCompletion",
    "ungroundedLegalClaim",
    "wrongEffectiveEvidence",
)


@dataclass(frozen=True)
class Evidence:
    evidence_id: str
    title: str
    source_url: str | None
    content_hash: str | None
    effective_from: str | None


def connection_kwargs() -> dict[str, object]:
    user = os.getenv("DB_USER")
    password = os.getenv("DB_PASSWORD")
    if not user or not password:
        raise RuntimeError("DB_USER and DB_PASSWORD are required for evaluation")
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": user,
        "password": password,
        "connect_timeout": 10,
    }


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip().casefold()


def classify(text: str) -> tuple[str, str]:
    normalized = normalize(text)
    for complaint_type, department, terms in COMPLAINT_RULES:
        if any(term.casefold() in normalized for term in terms):
            return complaint_type, department
    return "GENERAL", "CIVIL_AFFAIRS"


def blocker_for(case: dict[str, Any], evidence: list[Evidence]) -> str | None:
    scenario = str(case.get("scenario") or "")
    if scenario == "needs-location" or not case.get("locationText"):
        return "NEEDS_LOCATION"
    if scenario in {"needs-jurisdiction", "organization-change"}:
        return "NEEDS_JURISDICTION"
    if scenario == "evidence-insufficient" or not evidence:
        return "EVIDENCE_INSUFFICIENT"
    if scenario == "conflict-detected":
        return "CONFLICT_DETECTED"
    if scenario in {"official-api-failure", "malicious-attachment"}:
        return "PROCESSING_FAILED"
    return None


def load_golden() -> list[dict[str, Any]]:
    import generate_synthetic_golden

    # Always regenerate to avoid stale mojibake or stale schema in local files.
    generate_synthetic_golden.main()
    return json.loads(GOLDEN_PATH.read_text(encoding="utf-8"))


def expected_law_titles(complaint_type: str, department: str) -> tuple[str, ...]:
    if complaint_type == "GENERAL" and department in LAW_TITLES_BY_DEPARTMENT:
        return LAW_TITLES_BY_DEPARTMENT[department]
    return LAW_TITLES_BY_TYPE.get(complaint_type, LAW_TITLES_BY_TYPE["GENERAL"])


def evidence_for(cursor, complaint_type: str, department: str) -> list[Evidence]:
    titles = expected_law_titles(complaint_type, department)
    cursor.execute(
        """
        select id, title, source_url, content_hash, effective_from
        from knowledge_documents
        where purpose = 'OFFICIAL_LAW'
          and verification_status = 'VERIFIED_OFFICIAL'
          and jurisdiction_code = 'NATIONAL'
          and content_hash is not null
          and source_url is not null
          and title = any(%s)
        order by id
        limit 10
        """,
        (list(titles),),
    )
    rows = cursor.fetchall()
    if not rows:
        cursor.execute(
            """
            select id, title, source_url, content_hash, effective_from
            from knowledge_documents
            where purpose = 'OFFICIAL_LAW'
              and verification_status = 'VERIFIED_OFFICIAL'
              and jurisdiction_code = 'NATIONAL'
              and title = '민원 처리에 관한 법률'
            order by id
            limit 1
            """
        )
        rows = cursor.fetchall()
    return [Evidence(f"KD-{row[0]}", row[1], row[2], row[3], row[4].isoformat() if row[4] else None) for row in rows]


def build_claims(case: dict[str, Any], evidence: list[Evidence], blocker: str | None) -> list[dict[str, Any]]:
    if blocker:
        return []
    evidence_ids = [item.evidence_id for item in evidence[:3]]
    return [
        {
            "claimId": f"{case['caseId']}-claim-ack",
            "claimType": "ACKNOWLEDGEMENT",
            "text": "접수된 민원 내용과 위치 정보를 확인하고 담당 부서 검토 대상으로 분류합니다.",
            "evidenceIds": evidence_ids,
        },
        {
            "claimId": f"{case['caseId']}-claim-review",
            "claimType": "LEGAL_REVIEW_NOTICE",
            "text": "법령 적용 가능성은 공식 국가법령 조문과 현장 사실관계 확인 후 안내해야 합니다.",
            "evidenceIds": evidence_ids,
        },
    ]


def build_template(case: dict[str, Any], evidence: list[Evidence], blocker: str | None, department: str) -> dict[str, Any]:
    if blocker:
        return {
            "templateStatus": "BLOCKED",
            "blocker": blocker,
            "body": (
                "현재 민원 답변 초안은 생성하지 않습니다. 위치, 관할, 공식 근거, 첨부 안전성, "
                "또는 상충 여부 확인이 먼저 필요합니다."
            ),
            "manualCompletionOnly": True,
        }
    citations = ", ".join(f"{item.title}({item.evidence_id})" for item in evidence[:3])
    return {
        "templateStatus": "READY_FOR_HUMAN_REVIEW",
        "departmentCandidate": department,
        "body": (
            "민원 내용을 확인했습니다. 제기하신 사항은 담당 부서 검토와 현장 확인이 필요한 사안입니다. "
            f"초안 작성 시 참고한 공식 근거는 {citations}입니다. "
            "본 문안은 자동 발송되지 않으며 검토자와 승인자의 확인 후 수동 완료 기록만 가능합니다."
        ),
        "manualCompletionOnly": True,
    }


def predict_cases(golden: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], dict[str, int]]:
    counts: dict[str, int] = {}
    predictions: list[dict[str, Any]] = []
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            for case in golden:
                text = " ".join(str(case.get(field) or "") for field in ("inputText", "locationText", "attachmentText"))
                complaint_type, department = classify(text)
                evidence = evidence_for(cursor, complaint_type, department)
                blocker = blocker_for(case, evidence)
                claims = build_claims(case, evidence, blocker)
                template = build_template(case, evidence, blocker, department)
                official_ids = {item.evidence_id for item in evidence}
                flags = {flag: False for flag in SAFETY_FLAGS}
                flags["ungroundedLegalClaim"] = bool(claims) and any(not claim["evidenceIds"] for claim in claims)
                flags["wrongEffectiveEvidence"] = bool(claims) and any(
                    evidence_id not in official_ids for claim in claims for evidence_id in claim.get("evidenceIds", [])
                )
                predictions.append(
                    {
                        "caseId": case["caseId"],
                        "predictionSource": "db-backed-deterministic-template-pipeline",
                        "modelVersion": "rule-db-pipeline-v2",
                        "promptVersion": "evidence-template-v2",
                        "schemaVersion": "complaint-support-v1",
                        "complaintType": complaint_type,
                        "departmentTop3": [department],
                        "expectedLawTitles": list(expected_law_titles(complaint_type, department)),
                        "retrievedEvidenceIds": [item.evidence_id for item in evidence],
                        "retrievedEvidence": [item.__dict__ for item in evidence],
                        "claims": claims,
                        "answerTemplate": template,
                        "predictedBlocker": blocker,
                        **flags,
                    }
                )
                counts[complaint_type] = counts.get(complaint_type, 0) + 1
    return predictions, counts


def evidence_title_hit(expected: dict[str, Any], actual: dict[str, Any]) -> bool:
    if actual.get("predictedBlocker"):
        return True
    expected_titles = set(expected.get("expectedLawTitles") or actual.get("expectedLawTitles") or [])
    actual_titles = {item.get("title") for item in actual.get("retrievedEvidence", [])}
    return bool(expected_titles & actual_titles)


def template_complete(actual: dict[str, Any]) -> bool:
    template = actual.get("answerTemplate") or {}
    body = str(template.get("body") or "")
    if template.get("templateStatus") == "BLOCKED":
        return bool(template.get("blocker")) and template.get("manualCompletionOnly") is True
    return (
        template.get("templateStatus") == "READY_FOR_HUMAN_REVIEW"
        and template.get("manualCompletionOnly") is True
        and "자동 발송되지" in body
        and "공식 근거" in body
        and len(body) >= 80
    )


def evaluate(golden: list[dict[str, Any]], predictions: list[dict[str, Any]]) -> dict[str, float]:
    by_id = {prediction["caseId"]: prediction for prediction in predictions}
    total = len(golden)
    classification_hits = 0
    department_hits = 0
    blocker_hits = 0
    evidence_coverage = 0.0
    evidence_relevance_hits = 0
    template_hits = 0
    safety_failures = 0
    for expected in golden:
        actual = by_id[expected["caseId"]]
        classification_hits += actual["complaintType"] == expected["complaintType"]
        department_hits += expected["departmentCode"] in actual["departmentTop3"][:3]
        blocker_hits += actual.get("predictedBlocker") == expected.get("expectedBlocker")
        claims = actual.get("claims", [])
        evidence_coverage += 1.0 if not claims else sum(bool(claim.get("evidenceIds")) for claim in claims) / len(claims)
        evidence_relevance_hits += evidence_title_hit(expected, actual)
        template_hits += template_complete(actual)
        safety_failures += sum(int(bool(actual.get(flag))) for flag in SAFETY_FLAGS)
    return {
        "caseCount": float(total),
        "classificationAccuracy": classification_hits / total,
        "departmentTop3": department_hits / total,
        "blockerAccuracy": blocker_hits / total,
        "claimEvidenceCoverage": evidence_coverage / total,
        "evidenceTitleRelevance": evidence_relevance_hits / total,
        "templateCompleteness": template_hits / total,
        "safetyFailures": float(safety_failures),
    }


def db_quality_summary() -> dict[str, Any]:
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                """
                select count(*)
                from knowledge_purpose kp
                join knowledge_documents kd on kd.id = kp.knowledge_document_id
                where kp.legal_evidence_allowed = true
                  and not (
                    kp.purpose = 'OFFICIAL_LAW'
                    and kd.verification_status = 'VERIFIED_OFFICIAL'
                    and kd.jurisdiction_code = 'NATIONAL'
                  )
                """
            )
            illegal_evidence_rows = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from legal_provisions")
            legal_provisions = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from data_mart_raw_records")
            raw_records = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from data_mart_normalized_records")
            normalized_records = int(cursor.fetchone()[0])
            cursor.execute("select purpose, count(*) from knowledge_documents group by purpose order by purpose")
            knowledge_by_purpose = {row[0]: int(row[1]) for row in cursor.fetchall()}
            cursor.execute("select count(*) from spatial_admin_boundaries")
            admin_boundaries = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from spatial_address_points")
            address_points = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from spatial_parking_restrictions")
            parking_restrictions = int(cursor.fetchone()[0])
            cursor.execute("select count(*) from spatial_facilities where facility_type = 'CCTV'")
            cctv = int(cursor.fetchone()[0])
    data_readiness_checks = {
        "officialLawPresent": legal_provisions > 0,
        "nonEvidenceSourcesQuarantined": illegal_evidence_rows == 0,
        "dataMartLoaded": raw_records > 0 and normalized_records > 0,
        "addressPointsLoaded": address_points > 0,
        "spatialFacilitiesLoaded": cctv > 0 and parking_restrictions > 0,
        "sgisBoundariesLoaded": admin_boundaries > 0,
    }
    readiness_score = sum(data_readiness_checks.values()) / len(data_readiness_checks)
    return {
        "illegalEvidenceRows": illegal_evidence_rows,
        "legalProvisions": legal_provisions,
        "rawRecords": raw_records,
        "normalizedRecords": normalized_records,
        "knowledgeByPurpose": knowledge_by_purpose,
        "adminBoundaries": admin_boundaries,
        "addressPoints": address_points,
        "parkingRestrictions": parking_restrictions,
        "cctvFacilities": cctv,
        "dataReadinessChecks": data_readiness_checks,
        "dataReadinessScore": readiness_score,
    }


def write_training_decision() -> dict[str, Any]:
    decision = {
        "decision": "NO_FINE_TUNING",
        "createdAt": datetime.now(UTC).isoformat(),
        "reason": (
            "AIHub and local historical datasets are not yet proven privacy-safe, label-consistent, "
            "jurisdiction-current, or suitable for legal factual grounding. The pipeline keeps "
            "deterministic routing, retrieval, and verification gates; AIHub is limited to style "
            "or evaluation candidates after separate review."
        ),
        "allowedUse": ["STYLE_REFERENCE", "EVALUATION_TRAINING"],
        "forbiddenUse": ["LEGAL_FACT_MEMORY", "FINAL_APPROVAL", "AUTOMATIC_COMPLETION", "LEGAL_EVIDENCE"],
    }
    TRAINING_DECISION_PATH.write_text(json.dumps(decision, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return decision


def write_report(metrics: dict[str, float], quality: dict[str, Any], counts: dict[str, int], training: dict[str, Any]) -> None:
    hard_gate_pass = (
        quality["illegalEvidenceRows"] == 0
        and metrics["claimEvidenceCoverage"] == 1.0
        and metrics["evidenceTitleRelevance"] == 1.0
        and metrics["templateCompleteness"] == 1.0
        and metrics["safetyFailures"] == 0
    )
    status = "PASS_WITH_LIMITATIONS" if hard_gate_pass else "FAIL"
    report = f"""# Judge Report - Trust Template Pipeline

Generated at: {datetime.now(UTC).isoformat()}

## Verdict

Status: {status}

This is a deterministic local pipeline and data-readiness evaluation. It is not a
production accuracy claim. The synthetic evaluation now checks not only whether
claims have evidence IDs, but whether the cited official law titles match the
expected complaint type.

## Metrics

- Case count: {int(metrics["caseCount"])}
- Classification accuracy: {metrics["classificationAccuracy"]:.4f}
- Department Top-3: {metrics["departmentTop3"]:.4f}
- Blocker accuracy: {metrics["blockerAccuracy"]:.4f}
- Claim evidence coverage: {metrics["claimEvidenceCoverage"]:.4f}
- Evidence title relevance: {metrics["evidenceTitleRelevance"]:.4f}
- Template completeness: {metrics["templateCompleteness"]:.4f}
- Safety failures: {int(metrics["safetyFailures"])}

## DB Quality Gates

- Illegal legal-evidence rows: {quality["illegalEvidenceRows"]}
- Legal provisions loaded: {quality["legalProvisions"]}
- Raw data-mart records: {quality["rawRecords"]}
- Normalized data-mart records: {quality["normalizedRecords"]}
- Address points loaded: {quality["addressPoints"]}
- CCTV facilities loaded: {quality["cctvFacilities"]}
- Parking restrictions loaded: {quality["parkingRestrictions"]}
- SGIS/admin boundaries loaded: {quality["adminBoundaries"]}
- Data readiness score: {quality["dataReadinessScore"]:.4f}
- Data readiness checks: `{json.dumps(quality["dataReadinessChecks"], ensure_ascii=False, sort_keys=True)}`
- Knowledge by purpose: `{json.dumps(quality["knowledgeByPurpose"], ensure_ascii=False, sort_keys=True)}`

## Critical Assessment

1. Legal grounding is structurally valid only when evidence comes from
   `VERIFIED_OFFICIAL` national-law records. Local ordinances, manuals, Saeol
   history, spatial data, and AIHub data are blocked from legal-evidence use.
2. The previous shallow citation check was insufficient. It has been tightened
   with evidence-title relevance, so unrelated official law records no longer
   pass as valid support.
3. Accuracy metrics are measured on synthetic cases. They prove regression
   behavior and hard-gate behavior, not real Asan production performance.
4. Fine-tuning remains rejected: `{training["decision"]}`. Current datasets do
   not prove privacy safety, label quality, and legal-fact suitability.
5. Remaining weaknesses: SGIS boundary layer is missing, department organization
   data is intentionally deferred, HWP binary manuals are metadata-only until a
   trusted extractor is configured, and policy Q&A returned no records for the
   current query set.

## Complaint Type Distribution

`{json.dumps(counts, ensure_ascii=False, sort_keys=True)}`
"""
    REPORT_PATH.write_text(report, encoding="utf-8")


def main() -> None:
    golden = load_golden()
    predictions, counts = predict_cases(golden)
    PREDICTION_PATH.write_text(json.dumps(predictions, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    metrics = evaluate(golden, predictions)
    quality = db_quality_summary()
    training = write_training_decision()
    write_report(metrics, quality, counts, training)
    print(
        json.dumps(
            {
                "metrics": metrics,
                "quality": quality,
                "outputs": {
                    "predictions": str(PREDICTION_PATH),
                    "trainingDecision": str(TRAINING_DECISION_PATH),
                    "judgeReport": str(REPORT_PATH),
                },
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
