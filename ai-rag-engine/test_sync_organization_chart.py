import unittest

from sync_organization_chart import infer_complaint_types, parse_records


class SyncOrganizationChartTest(unittest.TestCase):
    def test_parse_records_from_docx_paragraph_stream(self):
        paragraphs = [
            "번호",
            "부서/팀명",
            "1",
            "도로관리팀",
            "주무관",
            "김○○",
            "041-540-1234",
            "도로 보수 및 포트홀 민원 처리",
            "2",
            "민원팀",
            "주무관",
            "박○○",
            "041-540-5678",
            "일반 민원 접수 및 처리",
        ]

        records = parse_records(paragraphs)

        self.assertEqual(2, len(records))
        self.assertEqual("도로관리팀", records[0].unit_name)
        self.assertEqual("041-540-1234", records[0].phone)
        self.assertNotIn("김", records[0].__dict__)

    def test_infer_complaint_type_from_unit_duty(self):
        record = parse_records([
            "1",
            "교통지도팀",
            "주무관",
            "정○○",
            "041-540-0000",
            "불법 주정차 단속 및 교통 민원 처리",
        ])[0]

        self.assertIn("ILLEGAL_PARKING", infer_complaint_types(record))


if __name__ == "__main__":
    unittest.main()
