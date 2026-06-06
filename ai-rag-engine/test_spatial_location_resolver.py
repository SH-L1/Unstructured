import unittest

from spatial_location_resolver import extract_search_terms, score_candidate


class SpatialLocationResolverTest(unittest.TestCase):
    def test_extracts_location_terms_without_using_llm_coordinates(self):
        terms = extract_search_terms("아산시 배방읍 모산로 공영주차장 앞 불법주정차 신고")

        self.assertIn("배방읍", terms)
        self.assertIn("모산로", terms)
        self.assertIn("공영주차장", terms)
        self.assertNotIn("신고", terms)

    def test_scores_exact_facility_match_high(self):
        confidence, score = score_candidate(
            "배방읍 공영주차장 앞",
            "배방읍 공영주차장",
            "충청남도 아산시 배방읍 모산로 1",
        )

        self.assertEqual("HIGH", confidence)
        self.assertGreaterEqual(score, 0.9)

    def test_scores_partial_address_match_medium(self):
        confidence, score = score_candidate(
            "온천대로 도로 파손",
            "온천대로",
            "충청남도 아산시 온천대로 100",
        )

        self.assertIn(confidence, {"HIGH", "MEDIUM"})
        self.assertGreaterEqual(score, 0.7)


if __name__ == "__main__":
    unittest.main()
