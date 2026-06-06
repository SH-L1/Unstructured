import unittest

from sync_auxiliary_sources import AuxiliarySource, build_auxiliary_document


class AuxiliarySourceSyncTest(unittest.TestCase):
    def test_builds_stable_non_legal_auxiliary_document(self):
        source = AuxiliarySource(
            name="Example API",
            source_type="EXAMPLE_API",
            base_url="https://example.test",
            base_url_getter=lambda: "https://example.test",
            purpose="PROCEDURE",
            document_type="FAQ",
            fetcher=lambda query, display: [],
        )

        document = build_auxiliary_document(
            source,
            "road damage",
            {
                "title": "Road damage FAQ",
                "source_name": "faq",
                "content": "Use this only as procedural reference.",
            },
        )

        self.assertIsNotNone(document)
        self.assertEqual("PROCEDURE", document.source.purpose)
        self.assertEqual("FAQ", document.source.document_type)
        self.assertEqual(64, len(document.content_hash))
        self.assertEqual("https://example.test", document.source_url)

    def test_skips_empty_api_documents(self):
        source = AuxiliarySource(
            name="Example API",
            source_type="EXAMPLE_API",
            base_url="https://example.test",
            base_url_getter=lambda: "https://example.test",
            purpose="HISTORICAL_CASE",
            document_type="CASE",
            fetcher=lambda query, display: [],
        )

        self.assertIsNone(build_auxiliary_document(source, "waste", {"title": "", "content": "body"}))
        self.assertIsNone(build_auxiliary_document(source, "waste", {"title": "title", "content": ""}))


if __name__ == "__main__":
    unittest.main()
