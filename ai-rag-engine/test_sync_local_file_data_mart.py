import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from sync_local_file_data_mart import LocalSource, extract_normalized_text, iter_files, run_text_command


class SyncLocalFileDataMartTest(unittest.TestCase):
    def test_aihub_zip_promotes_internal_json_text_not_only_manifest(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            zip_path = Path(temp_dir) / "aihub.zip"
            with zipfile.ZipFile(zip_path, "w") as archive:
                archive.writestr(
                    "Training/label.json",
                    '{"instruction": "민원 답변 작성", "output": "도로 파손은 담당 부서 검토가 필요합니다."}',
                )
            source = LocalSource(
                "AIHUB_PUBLIC_COMPLAINT_LLM_FILES",
                "AIHub public civil complaint LLM dataset",
                Path(temp_dir),
                "STYLE_REFERENCE",
                "SYNTHETIC",
            )

            extracted = extract_normalized_text(zip_path, source)

        self.assertIsNotNone(extracted)
        content, metadata = extracted
        self.assertIn("민원 답변 작성", content)
        self.assertEqual("zip-payload-text", metadata["extractor"])

    @patch("sync_local_file_data_mart.subprocess.run")
    def test_text_command_passes_spaced_input_as_single_argument(self, run):
        run.return_value = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="ok".encode("utf-8"),
            stderr=b"",
        )

        run_text_command("hwp5txt.exe {input}", Path("민원 편람 (3).hwp"), "HWP")

        self.assertEqual(["hwp5txt.exe", "민원 편람 (3).hwp"], run.call_args.args[0])

    @patch.dict("os.environ", {"LOCAL_FILE_MAX_FILES_PER_SOURCE": "2"}, clear=False)
    def test_iter_files_can_be_bounded_for_large_datasets(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for index in range(5):
                (root / f"{index}.json").write_text("{}", encoding="utf-8")
            source = LocalSource("AIHUB_PUBLIC_COMPLAINT_LLM_FILES", "AIHub", root, "STYLE_REFERENCE", "SYNTHETIC")

            files = list(iter_files(source))

        self.assertEqual(2, len(files))


if __name__ == "__main__":
    unittest.main()
