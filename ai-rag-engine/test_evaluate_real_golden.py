import unittest

from evaluate_real_golden import validate_real_golden
from evaluate_golden import normalized_input_hash


def reviewed_case(case_id="REAL-001"):
    case = {
        "caseId": case_id,
        "split": "test",
        "inputText": "Anonymized complaint text with no personal data.",
        "locationText": "Anonymized district",
        "attachmentText": "",
        "syntheticDemo": False,
        "containsRealPersonalData": False,
        "complaintType": "ILLEGAL_PARKING",
        "departmentCode": "TRAFFIC",
        "requiredEvidenceIds": ["LAW-ROAD-TRAFFIC"],
        "expectedBlocker": None,
        "anonymizationReview": {
            "status": "APPROVED",
            "reviewerRole": "data-steward",
            "reviewedAt": "2026-06-07",
        },
        "labelReview": {
            "status": "APPROVED",
            "labelSource": "DUAL_REVIEW",
            "reviewerRole": "domain-reviewer",
            "reviewedAt": "2026-06-07",
        },
        "allowedUses": [
            "EVALUATION_ONLY",
            "DEPARTMENT_TOP3_EVALUATION",
            "RETRIEVAL_RECALL_EVALUATION",
        ],
        "forbiddenUses": [
            "LEGAL_FACT_MEMORY",
            "FINAL_APPROVAL",
            "AUTOMATIC_COMPLETION",
            "AUTOMATIC_SEND",
        ],
    }
    case["inputHash"] = normalized_input_hash(case)
    return case


class RealGoldenEvaluationTest(unittest.TestCase):
    def test_accepts_reviewed_privacy_safe_real_case(self):
        validate_real_golden([reviewed_case()], require_full=False)

    def test_rejects_unreviewed_or_personal_data_cases(self):
        unsafe = reviewed_case()
        unsafe["containsRealPersonalData"] = True
        with self.assertRaises(ValueError):
            validate_real_golden([unsafe], require_full=False)

        unreviewed = reviewed_case("REAL-002")
        unreviewed["labelReview"]["status"] = "PENDING"
        with self.assertRaises(ValueError):
            validate_real_golden([unreviewed], require_full=False)

    def test_rejects_missing_usage_gates_or_mismatched_hash(self):
        missing_policy = reviewed_case()
        missing_policy["forbiddenUses"] = ["AUTOMATIC_SEND"]
        with self.assertRaises(ValueError):
            validate_real_golden([missing_policy], require_full=False)

        tampered = reviewed_case("REAL-003")
        tampered["inputText"] = "Different anonymized complaint text."
        with self.assertRaises(ValueError):
            validate_real_golden([tampered], require_full=False)


if __name__ == "__main__":
    unittest.main()
