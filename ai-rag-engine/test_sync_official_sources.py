import unittest
import xml.etree.ElementTree as ET

from sync_official_sources import build_document, parse_provisions


SAMPLE_XML = """
<법령>
  <시행일자>20260101</시행일자>
  <조문단위>
    <조문번호>1</조문번호>
    <조문제목>목적</조문제목>
    <조문내용>제1조 이 법은 민원의 공정한 처리를 목적으로 한다.</조문내용>
  </조문단위>
  <조문단위>
    <조문번호>2</조문번호>
    <조문내용>제2조 신고 내용은 검토하여야 한다.</조문내용>
  </조문단위>
</법령>
"""


class OfficialSourceSyncTest(unittest.TestCase):
    def test_preserves_provisions_and_versions_content(self):
        root = ET.fromstring(SAMPLE_XML)
        provisions = parse_provisions(root)
        self.assertEqual(["제1조", "제2조"], [item.key for item in provisions])

        document = build_document(
            {
                "law_id": "TEST-LAW-1",
                "title": "민원 처리에 관한 법률",
                "detail_link": "/DRF/lawService.do?target=law&MST=TEST-LAW-1",
            },
            root,
        )
        self.assertEqual("2026-01-01", document.effective_from.isoformat())
        self.assertEqual(64, len(document.content_hash))
        self.assertIn("제1조", document.content)

    def test_rejects_documents_without_provisions(self):
        with self.assertRaises(ValueError):
            parse_provisions(ET.fromstring("<법령><시행일자>20260101</시행일자></법령>"))


if __name__ == "__main__":
    unittest.main()
