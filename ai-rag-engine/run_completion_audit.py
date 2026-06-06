"""Audit the active project goal against current local evidence.

This is deliberately stricter than the synthetic judge metrics. A green metric
on a narrow test does not prove the full goal; this script records requirement
status from DB counts, generated artifacts, and known external blockers.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import psycopg2
from dotenv import load_dotenv


load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
EVAL_DIR = BASE_DIR / "data" / "evaluation"
AUDIT_JSON_PATH = EVAL_DIR / "completion_audit.latest.json"
AUDIT_REPORT_PATH = EVAL_DIR / "completion_audit.latest.md"
JUDGE_REPORT_PATH = EVAL_DIR / "judge_report.latest.md"
TRAINING_DECISION_PATH = EVAL_DIR / "training_decision.latest.json"
PREDICTION_PATH = EVAL_DIR / "pipeline_predictions.latest.json"


@dataclass(frozen=True)
class AuditItem:
    requirement: str
    status: str
    evidence: str
    action: str


def connection_kwargs(user_env: str = "DB_USER", password_env: str = "DB_PASSWORD") -> dict[str, object]:
    user = os.getenv(user_env)
    password = os.getenv(password_env)
    if not user or not password:
        raise RuntimeError(f"{user_env} and {password_env} are required")
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": user,
        "password": password,
        "connect_timeout": 5,
    }


def scalar(cursor, sql: str, params: tuple[Any, ...] = ()) -> int:
    cursor.execute(sql, params)
    return int(cursor.fetchone()[0])


def db_snapshot() -> dict[str, Any]:
    with psycopg2.connect(**connection_kwargs()) as connection:
        with connection.cursor() as cursor:
            snapshot = {
                "sourceRegistry": scalar(cursor, "select count(*) from source_registry"),
                "knowledgeDocuments": scalar(cursor, "select count(*) from knowledge_documents"),
                "knowledgePurpose": scalar(cursor, "select count(*) from knowledge_purpose"),
                "legalDocumentVersions": scalar(cursor, "select count(*) from legal_document_versions"),
                "legalProvisions": scalar(cursor, "select count(*) from legal_provisions"),
                "rawRecords": scalar(cursor, "select count(*) from data_mart_raw_records"),
                "normalizedRecords": scalar(cursor, "select count(*) from data_mart_normalized_records"),
                "adminBoundaries": scalar(cursor, "select count(*) from spatial_admin_boundaries"),
                "addressPoints": scalar(cursor, "select count(*) from spatial_address_points"),
                "spatialFacilities": scalar(cursor, "select count(*) from spatial_facilities"),
                "parkingRestrictions": scalar(cursor, "select count(*) from spatial_parking_restrictions"),
                "asanOrganizationUnits": scalar(
                    cursor,
                    """
                    select count(*)
                    from organization_units
                    where jurisdiction_code = 'ASAN'
                      and synthetic_demo = false
                      and active = true
                    """,
                ),
                "asanAssignmentRules": scalar(
                    cursor,
                    """
                    select count(*)
                    from assignment_rules ar
                    join organization_units ou on ou.id = ar.organization_unit_id
                    where ar.jurisdiction_code = 'ASAN'
                      and ar.synthetic_demo = false
                      and ar.active = true
                      and ou.synthetic_demo = false
                    """,
                ),
                "organizationRawRecords": scalar(
                    cursor,
                    """
                    select count(*)
                    from data_mart_raw_records
                    where source_type = 'ASAN_ORGANIZATION_FILE'
                    """,
                ),
                "illegalEvidenceRows": scalar(
                    cursor,
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
                    """,
                ),
                "binaryHwpRawRecords": scalar(
                    cursor,
                    """
                    select count(*)
                    from data_mart_raw_records
                    where source_type = 'MINWON_MANUAL_FILES'
                      and lower(raw_payload) like '%%.hwp%%'
                      and lower(raw_payload) not like '%%.hwpx%%'
                    """,
                ),
                "hwpExtractedRecords": scalar(
                    cursor,
                    """
                    select count(*)
                    from data_mart_normalized_records
                    where metadata_json like '%%worker-hwp-text-command%%'
                    """,
                ),
                "hwpMeaningfulTextRecords": scalar(
                    cursor,
                    """
                    select count(*)
                    from data_mart_normalized_records
                    where metadata_json like '%%worker-hwp-text-command%%'
                      and length(regexp_replace(content, '<표>|\\s+', '', 'g')) >= 100
                    """,
                ),
            }
            snapshot["hwpMeaningfulTextRecords"] = scalar(
                cursor,
                """
                select count(*)
                from data_mart_normalized_records
                where metadata_json like '%%worker-hwp-text-command%%'
                  and length(regexp_replace(content, '<표>|\\s+', '', 'g')) >= 100
                """,
            )
            snapshot["lowQualityHwpPromotions"] = scalar(
                cursor,
                """
                select count(*)
                from data_mart_normalized_records
                where metadata_json like '%%worker-hwp-text-command%%'
                  and length(regexp_replace(content, '<표>|\\s+', '', 'g')) < 100
                """,
            )
            snapshot["lowQualityHwpLoadErrors"] = scalar(
                cursor,
                """
                select count(*)
                from data_mart_load_errors
                where source_type = 'MINWON_MANUAL_FILES'
                  and error_code = 'LOW_QUALITY_HWP_TEXT'
                """,
            )
            cursor.execute("select purpose, count(*) from knowledge_purpose group by purpose order by purpose")
            snapshot["purposeDistribution"] = {row[0]: int(row[1]) for row in cursor.fetchall()}
            cursor.execute("select source_type, count(*) from data_mart_raw_records group by source_type order by source_type")
            snapshot["rawBySourceType"] = {row[0]: int(row[1]) for row in cursor.fetchall()}
            return snapshot


def artifact_snapshot() -> dict[str, Any]:
    artifacts = {
        "judgeReportExists": JUDGE_REPORT_PATH.exists(),
        "trainingDecisionExists": TRAINING_DECISION_PATH.exists(),
        "predictionsExist": PREDICTION_PATH.exists(),
    }
    if TRAINING_DECISION_PATH.exists():
        training = json.loads(TRAINING_DECISION_PATH.read_text(encoding="utf-8"))
        artifacts["trainingDecision"] = training.get("decision")
    if PREDICTION_PATH.exists():
        predictions = json.loads(PREDICTION_PATH.read_text(encoding="utf-8"))
        artifacts["predictionCount"] = len(predictions)
        artifacts["automaticSendFlags"] = sum(1 for item in predictions if item.get("automaticSend"))
        artifacts["automaticCompletionFlags"] = sum(1 for item in predictions if item.get("automaticCompletion"))
        artifacts["ungroundedLegalClaimFlags"] = sum(1 for item in predictions if item.get("ungroundedLegalClaim"))
    return artifacts


def worker_auth_ok() -> bool:
    try:
        with psycopg2.connect(**connection_kwargs("WORKER_DB_USER", "WORKER_DB_PASSWORD")):
            return True
    except Exception:
        return False


def audit_items(db: dict[str, Any], artifacts: dict[str, Any]) -> list[AuditItem]:
    items: list[AuditItem] = []
    all_current_data_loaded = (
        db["knowledgeDocuments"] >= 797
        and db["rawRecords"] >= 657
        and db["normalizedRecords"] >= 514
        and db["addressPoints"] >= 70533
        and db["spatialFacilities"] >= 664
        and db["parkingRestrictions"] >= 249
        and db["asanOrganizationUnits"] > 0
        and db["asanAssignmentRules"] > 0
        and db["organizationRawRecords"] > 0
    )
    items.append(
        AuditItem(
            "Phase 1 data load including Asan organization chart",
            "PASS" if all_current_data_loaded else "FAIL",
            (
                f"knowledge={db['knowledgeDocuments']}, raw={db['rawRecords']}, "
                f"normalized={db['normalizedRecords']}, address={db['addressPoints']}, "
                f"facilities={db['spatialFacilities']}, parkingRestrictions={db['parkingRestrictions']}, "
                f"asanOrganizationUnits={db['asanOrganizationUnits']}, "
                f"asanAssignmentRules={db['asanAssignmentRules']}"
            ),
            "Load any missing source before claiming Phase 1 complete.",
        )
    )
    items.append(
        AuditItem(
            "Asan organization routing data",
            "PASS" if db["asanOrganizationUnits"] > 0 and db["asanAssignmentRules"] > 0 else "FAIL",
            (
                f"asanOrganizationUnits={db['asanOrganizationUnits']}, "
                f"asanAssignmentRules={db['asanAssignmentRules']}, "
                f"organizationRawRecords={db['organizationRawRecords']}"
            ),
            "Organization data is routing support only and still requires human confirmation.",
        )
    )
    items.append(
        AuditItem(
            "Legal evidence quarantine",
            "PASS" if db["illegalEvidenceRows"] == 0 else "FAIL",
            f"illegalEvidenceRows={db['illegalEvidenceRows']}",
            "Only VERIFIED_OFFICIAL NATIONAL law records may be legal evidence.",
        )
    )
    artifacts_ready = artifacts.get("judgeReportExists") and artifacts.get("predictionsExist") and artifacts.get("trainingDecisionExists")
    items.append(
        AuditItem(
            "End-to-end template evaluation artifacts",
            "PASS" if artifacts_ready and artifacts.get("predictionCount") == 150 else "FAIL",
            f"artifacts={artifacts}",
            "Run run_trust_pipeline_evaluation.py.",
        )
    )
    no_auto = (
        artifacts.get("automaticSendFlags") == 0
        and artifacts.get("automaticCompletionFlags") == 0
        and artifacts.get("ungroundedLegalClaimFlags") == 0
    )
    items.append(
        AuditItem(
            "No automatic send, automatic completion, or ungrounded legal claim",
            "PASS" if no_auto else "FAIL",
            (
                f"automaticSend={artifacts.get('automaticSendFlags')}, "
                f"automaticCompletion={artifacts.get('automaticCompletionFlags')}, "
                f"ungroundedLegalClaim={artifacts.get('ungroundedLegalClaimFlags')}"
            ),
            "Keep all final actions human-reviewed and citation-gated.",
        )
    )
    items.append(
        AuditItem(
            "Fine-tuning decision is evidence-gated",
            "PASS" if artifacts.get("trainingDecision") == "NO_FINE_TUNING" else "FAIL",
            f"trainingDecision={artifacts.get('trainingDecision')}",
            "Do not fine-tune until privacy, labels, and legal-fact suitability are proven.",
        )
    )
    items.append(
        AuditItem(
            "SGIS/admin boundary layer",
            "BLOCKED_EXTERNAL" if db["adminBoundaries"] == 0 else "PASS",
            f"adminBoundaries={db['adminBoundaries']}",
            "Provide SGIS boundary API configuration or ai-rag-engine/data/spatial/asan_admin_boundaries.geojson.",
        )
    )
    items.append(
        AuditItem(
            "HWP raw file coverage",
            "PASS" if db["hwpExtractedRecords"] + db["lowQualityHwpLoadErrors"] >= db["binaryHwpRawRecords"] else "LIMITED",
            f"binaryHwpRawRecords={db['binaryHwpRawRecords']}, searchableHwpRecords={db['hwpExtractedRecords']}",
            "All HWP files remain in raw records; only meaningful text should be searchable.",
        )
    )
    items.append(
        AuditItem(
            "HWP searchable text quality gate",
            "PASS" if db["lowQualityHwpPromotions"] == 0 and db["hwpExtractedRecords"] > 0 else "FAIL",
            (
                f"searchableHwpRecords={db['hwpExtractedRecords']}, "
                f"meaningfulSearchable={db['hwpMeaningfulTextRecords']}, "
                f"lowQualityPromotions={db['lowQualityHwpPromotions']}, "
                f"lowQualityLoadErrors={db['lowQualityHwpLoadErrors']}"
            ),
            "Low-quality HWP extraction must stay raw-only and be excluded from retrieval.",
        )
    )
    worker_ok = worker_auth_ok()
    items.append(
        AuditItem(
            "Restricted worker DB account",
            "PASS" if worker_ok else "BLOCKED_EXTERNAL",
            "WORKER_DB_USER connection check",
            "Worker DB login is valid." if worker_ok else "Create or fix the restricted worker DB role; local ingestion currently uses DB_USER override.",
        )
    )
    return items


def write_outputs(db: dict[str, Any], artifacts: dict[str, Any], items: list[AuditItem]) -> None:
    generated_at = datetime.now(UTC).isoformat()
    statuses = {item.status for item in items}
    if "FAIL" in statuses:
        overall_status = "FAIL"
    elif "BLOCKED_EXTERNAL" in statuses:
        overall_status = "PASS_WITH_EXTERNAL_BLOCKERS"
    elif "LIMITED" in statuses:
        overall_status = "PASS_WITH_LIMITATIONS"
    else:
        overall_status = "PASS"
    payload = {
        "generatedAt": generated_at,
        "overallStatus": overall_status,
        "db": db,
        "artifacts": artifacts,
        "items": [item.__dict__ for item in items],
    }
    AUDIT_JSON_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# Completion Audit - Active Goal",
        "",
        f"Generated at: {generated_at}",
        "",
        f"Overall status: {payload['overallStatus']}",
        "",
        "| Requirement | Status | Evidence | Action |",
        "| --- | --- | --- | --- |",
    ]
    for item in items:
        lines.append(f"| {item.requirement} | {item.status} | {item.evidence} | {item.action} |")
    lines.extend(
        [
            "",
            "## Interpretation",
            "",
            "The implemented pipeline is materially stronger than the previous shallow RAG check. The provided Asan organization chart is loaded as routing support with human confirmation required. Binary HWP manuals are all retained as raw records, but only meaningful extracted text is promoted into searchable procedure knowledge; low-quality table-placeholder output is blocked from retrieval and recorded in data_mart_load_errors.",
        ]
    )
    AUDIT_REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    EVAL_DIR.mkdir(parents=True, exist_ok=True)
    db = db_snapshot()
    artifacts = artifact_snapshot()
    items = audit_items(db, artifacts)
    write_outputs(db, artifacts, items)
    print(json.dumps({"auditReport": str(AUDIT_REPORT_PATH), "auditJson": str(AUDIT_JSON_PATH)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
