import unittest
import xml.etree.ElementTree as ET

from sync_local_ordinances import build_document


SAMPLE_ASAN_ORDINANCE_XML = """
<자치법규>
  <자치단체명>아산시</자치단체명>
  <공포일자>20250101</공포일자>
  <시행일자>20250201</시행일자>
  <조문단위>
    <조문번호>1</조문번호>
    <조문제목>목적</조문제목>
    <조문내용>제1조 이 조례는 아산시 민원 처리 절차를 정하는 것을 목적으로 한다.</조문내용>
  </조문단위>
</자치법규>
"""


SAMPLE_OTHER_ORDINANCE_XML = """
<자치법규>
  <자치단체명>천안시</자치단체명>
  <시행일자>20250201</시행일자>
  <조문단위>
    <조문번호>1</조문번호>
    <조문내용>제1조 목적.</조문내용>
  </조문단위>
</자치법규>
"""


class LocalOrdinanceSyncTest(unittest.TestCase):
    def test_builds_asan_scoped_versioned_document(self):
        document = build_document(
            {
                "law_id": "ASAN-ORD-1",
                "title": "아산시 민원 처리 조례",
                "source_name": "아산시",
                "detail_link": "/DRF/lawService.do?target=ordin&ID=ASAN-ORD-1",
            },
            ET.fromstring(SAMPLE_ASAN_ORDINANCE_XML),
        )

        self.assertEqual("ASAN", document.jurisdiction_code)
        self.assertEqual("2025-01-01", document.promulgated_at.isoformat())
        self.assertEqual("2025-02-01", document.effective_from.isoformat())
        self.assertEqual(64, len(document.content_hash))
        self.assertEqual("제1조", document.provisions[0].key)
        self.assertIn("아산시 민원 처리 절차", document.content)

    def test_rejects_non_asan_ordinance(self):
        with self.assertRaises(ValueError):
            build_document(
                {
                    "law_id": "OTHER-ORD-1",
                    "title": "천안시 민원 처리 조례",
                    "source_name": "천안시",
                    "detail_link": "/DRF/lawService.do?target=ordin&ID=OTHER-ORD-1",
                },
                ET.fromstring(SAMPLE_OTHER_ORDINANCE_XML),
            )


if __name__ == "__main__":
    unittest.main()
