import hashlib
import os
import unittest
from datetime import date

from sync_opensearch_indices import IndexedDocument, delete_stale_index_documents, purpose_index_name, source_for_row


class OpenSearchIndexSyncTest(unittest.TestCase):
    def test_builds_purpose_specific_index_name(self):
        os.environ["OPENSEARCH_INDEX_PREFIX"] = "pilot"
        self.assertEqual("pilot-official-law", purpose_index_name("OFFICIAL_LAW"))
        with self.assertRaises(ValueError):
            purpose_index_name("UNVERIFIED_LEGACY")

    def test_official_law_requires_governed_hash_and_metadata(self):
        content = "제1조 합성 시험 조항"
        row = (
            1,
            "합성 시험 법령",
            "Korean National Law Information Center",
            "https://www.law.go.kr/DRF/lawService.do?ID=1",
            content,
            "합성,시험",
            "합성 시험 법령 제1조",
            "OFFICIAL_LAW",
            "VERIFIED_OFFICIAL",
            "NATIONAL",
            date(2026, 1, 1),
            None,
            hashlib.sha256(content.encode("utf-8")).hexdigest(),
            "1:abcdef",
            101,
            "제1조",
            "목적",
            content,
        )
        indexed = source_for_row(row, date(2026, 6, 4))
        self.assertEqual(1, indexed.document_id)
        self.assertEqual("1-101", indexed.index_id)
        self.assertEqual("OFFICIAL_LAW", indexed.purpose)
        self.assertIn("제1조", indexed.source["embeddingText"])

        invalid = list(row)
        invalid[12] = "wrong-hash"
        with self.assertRaises(ValueError):
            source_for_row(tuple(invalid), date(2026, 6, 4))

    def test_removes_documents_no_longer_present_in_governed_database(self):
        class FakeClient:
            def __init__(self):
                self.calls = []

            def delete_by_query(self, **kwargs):
                self.calls.append(kwargs)

        client = FakeClient()
        delete_stale_index_documents(
            client,
            [
                IndexedDocument(
                    document_id=1,
                    index_id="1-101",
                    purpose="OFFICIAL_LAW",
                    source={"documentId": 1},
                )
            ],
        )

        official = next(call for call in client.calls if call["index"].endswith("-official-law"))
        procedure = next(call for call in client.calls if call["index"].endswith("-procedure"))
        self.assertEqual(
            {"bool": {"must_not": [{"ids": {"values": ["1-101"]}}]}},
            official["body"]["query"],
        )
        self.assertEqual({"match_all": {}}, procedure["body"]["query"])


if __name__ == "__main__":
    unittest.main()
