import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from department_router import recommend_department


class DepartmentRouterTest(unittest.TestCase):
    def test_recommends_from_manual_file_names_without_xlsx_history(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            manual_dir = Path(temp_dir)
            (manual_dir / "2026_민원편람(도로관리과).hwpx").write_text("placeholder", encoding="utf-8")
            with patch.dict(
                os.environ,
                {
                    "MINWON_MANUAL_DIR": str(manual_dir),
                    "SAEOL_PUBLIC_COMPLAINTS_FILE": str(manual_dir / "missing"),
                    "DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE": "false",
                },
                clear=False,
            ):
                result = recommend_department("도로 포트홀 파손 보수 요청", top_k=3)

        self.assertEqual("도로관리과", result["recommended_department"])
        self.assertEqual("auxiliary_data", result["recommendation_source"])

    def test_recommends_from_saeol_reviewed_department_records(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            saeol_dir = Path(temp_dir)
            (saeol_dir / "saeol.json").write_text(
                """
                [
                  {
                    "제목": "불법 주정차 민원",
                    "민원내용": "초등학교 앞 불법 주정차 차량 단속 요청",
                    "처리부서": "교통행정과"
                  }
                ]
                """,
                encoding="utf-8",
            )
            with patch.dict(
                os.environ,
                {
                    "SAEOL_PUBLIC_COMPLAINTS_FILE": str(saeol_dir),
                    "MINWON_MANUAL_DIR": str(saeol_dir / "missing"),
                    "DEPARTMENT_HISTORY_APPROVED_FOR_AUXILIARY_USE": "false",
                },
                clear=False,
            ):
                result = recommend_department("학교 앞 주정차 차량 단속 요청", top_k=3)

        self.assertEqual("교통행정과", result["recommended_department"])
        self.assertEqual("SAEOL_PUBLIC_COMPLAINT", result["top_matches"][0]["source_type"])


if __name__ == "__main__":
    unittest.main()
