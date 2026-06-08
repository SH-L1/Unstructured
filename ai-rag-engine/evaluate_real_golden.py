"""Evaluate predictions against privacy-reviewed real complaint golden cases.

This evaluator is intentionally stricter than the synthetic evaluator. A real
case may be used for accuracy claims only when it has explicit anonymization and
label-review metadata. The script does not create labels; it blocks incomplete
or unsafe datasets from being used as a "real accuracy" benchmark.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from evaluate_golden import evaluate, load_cases, normalized_input_hash, validate_predictions


ALLOWED_LABEL_SOURCES = {"SAEOL_PROCESSED_DEPARTMENT", "EXPERT_REVIEW", "DUAL_REVIEW"}
ALLOWED_REVIEW_STATUS = {"APPROVED", "APPROVED_WITH_LIMITATIONS"}
REQUIRED_ALLOWED_USES = {
    "EVALUATION_ONLY",
    "DEPARTMENT_TOP3_EVALUATION",
    "RETRIEVAL_RECALL_EVALUATION",
}
REQUIRED_FORBIDDEN_USES = {
    "LEGAL_FACT_MEMORY",
    "FINAL_APPROVAL",
    "AUTOMATIC_COMPLETION",
    "AUTOMATIC_SEND",
}
REQUIRED_CASE_FIELDS = (
    "caseId",
    "split",
    "inputText",
    "inputHash",
    "complaintType",
    "departmentCode",
    "requiredEvidenceIds",
    "expectedBlocker",
    "containsRealPersonalData",
    "anonymizationReview",
    "labelReview",
    "allowedUses",
    "forbiddenUses",
)


def validate_real_golden(cases: list[dict], require_full: bool) -> None:
    if require_full and not 100 <= len(cases) <= 200:
        raise ValueError("Real golden dataset must contain between 100 and 200 reviewed cases")
    seen_inputs: dict[str, str] = {}
    split_counts = {"train": 0, "validation": 0, "test": 0}
    for case in cases:
        case_id = str(case.get("caseId") or "")
        missing = [field for field in REQUIRED_CASE_FIELDS if field not in case]
        if missing:
            raise ValueError(f"{case_id or '<missing caseId>'} is missing fields: {', '.join(missing)}")
        if case.get("syntheticDemo") is True:
            raise ValueError(f"{case_id} is synthetic and cannot be used as real golden data")
        if case.get("containsRealPersonalData") is not False:
            raise ValueError(f"{case_id} is not confirmed free of real personal data")
        split = case.get("split")
        if split not in split_counts:
            raise ValueError(f"{case_id} has invalid split: {split!r}")
        split_counts[split] += 1
        anonymization = case.get("anonymizationReview") or {}
        label_review = case.get("labelReview") or {}
        if anonymization.get("status") not in ALLOWED_REVIEW_STATUS:
            raise ValueError(f"{case_id} is missing approved anonymization review")
        if label_review.get("status") not in ALLOWED_REVIEW_STATUS:
            raise ValueError(f"{case_id} is missing approved label review")
        if label_review.get("labelSource") not in ALLOWED_LABEL_SOURCES:
            raise ValueError(f"{case_id} has unsupported label source: {label_review.get('labelSource')!r}")
        if not set(case.get("allowedUses") or ()).issuperset(REQUIRED_ALLOWED_USES):
            raise ValueError(f"{case_id} is missing required allowedUses for real evaluation")
        if not set(case.get("forbiddenUses") or ()).issuperset(REQUIRED_FORBIDDEN_USES):
            raise ValueError(f"{case_id} is missing required forbiddenUses safety gates")
        input_hash = case.get("inputHash")
        expected_hash = normalized_input_hash(case)
        if input_hash != expected_hash:
            raise ValueError(f"{case_id} inputHash does not match the anonymized input fields")
        if not input_hash:
            raise ValueError(f"{case_id} must contain a stable anonymized inputHash")
        if input_hash in seen_inputs:
            raise ValueError(f"Duplicate anonymized inputHash: {seen_inputs[input_hash]} and {case_id}")
        seen_inputs[input_hash] = case_id
    if require_full and any(count == 0 for count in split_counts.values()):
        raise ValueError("Real golden dataset must contain train, validation, and test splits")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("golden", type=Path)
    parser.add_argument("predictions", type=Path)
    parser.add_argument("--require-full", action="store_true")
    args = parser.parse_args()
    golden = load_cases(args.golden)
    predictions = load_cases(args.predictions)
    validate_real_golden(golden, args.require_full)
    validate_predictions(predictions)
    metrics = evaluate(golden, predictions)
    print(json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True))
    failed = (
        metrics["classificationAccuracy"] < 0.95
        or metrics["departmentTop3"] < 0.95
        or metrics["recallAt10"] < 0.95
        or metrics["mrrAt10"] < 0.90
        or metrics["ndcgAt10"] < 0.90
        or metrics["claimEvidenceCoverage"] < 1.0
        or metrics["safetyFailures"] != 0
    )
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
