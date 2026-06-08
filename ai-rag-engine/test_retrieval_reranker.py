import unittest
from datetime import date

from evaluate_golden import ndcg_at_k, reciprocal_rank_at_k, recall_at_k
from retrieval_reranker import RetrievalCandidate, candidate_score, rerank_candidates


class RetrievalRerankerTest(unittest.TestCase):
    def test_reranks_governed_relevant_official_law_above_noisy_match(self):
        query = "road damage pothole official legal review"
        noisy = RetrievalCandidate(
            evidence_id="noise",
            title="Road style example",
            content="road road road pothole",
            purpose="STYLE",
            verification_status="SYNTHETIC_DEMO",
            retriever_score=100.0,
        )
        governed = RetrievalCandidate(
            evidence_id="law",
            title="Road Act",
            content="official legal review for road damage and pothole repair",
            purpose="OFFICIAL_LAW",
            verification_status="VERIFIED_OFFICIAL",
            jurisdiction_code="NATIONAL",
            retriever_score=1.0,
            effective_from=date(2026, 1, 1),
        )

        ranked = rerank_candidates(query, [noisy, governed], expected_purpose="OFFICIAL_LAW", on_date=date(2026, 6, 7))

        self.assertEqual("law", ranked[0].evidence_id)
        self.assertGreater(
            candidate_score(query, governed, "OFFICIAL_LAW", date(2026, 6, 7)),
            candidate_score(query, noisy, "OFFICIAL_LAW", date(2026, 6, 7)),
        )

    def test_ranking_metrics_reward_early_relevant_evidence(self):
        expected = ["A", "B"]
        good = ["A", "C", "B"]
        late = ["C", "D", "A", "B"]

        self.assertEqual(1.0, recall_at_k(expected, good, 10))
        self.assertGreater(reciprocal_rank_at_k(expected, good, 10), reciprocal_rank_at_k(expected, late, 10))
        self.assertGreater(ndcg_at_k(expected, good, 10), ndcg_at_k(expected, late, 10))

    def test_style_reference_uses_named_project_purpose(self):
        style = RetrievalCandidate(
            evidence_id="style",
            title="민원 답변 문체",
            content="정중한 민원 답변 문체 예시",
            purpose="STYLE_REFERENCE",
            verification_status="VERIFIED_INTERNAL",
        )
        unknown = RetrievalCandidate(
            evidence_id="unknown",
            title="민원 답변 문체",
            content="정중한 민원 답변 문체 예시",
            purpose="UNKNOWN",
            verification_status="VERIFIED_INTERNAL",
        )

        self.assertGreater(
            candidate_score("민원 답변 문체", style, "STYLE_REFERENCE", date(2026, 6, 7)),
            candidate_score("민원 답변 문체", unknown, "STYLE_REFERENCE", date(2026, 6, 7)),
        )


if __name__ == "__main__":
    unittest.main()
