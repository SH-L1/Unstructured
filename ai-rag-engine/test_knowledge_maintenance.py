import os
import unittest
from unittest.mock import patch

import knowledge_maintenance


class KnowledgeMaintenanceTest(unittest.TestCase):
    def test_runs_due_source_then_refreshes_derived_indices(self):
        with (
            patch.dict(
                os.environ,
                {"OFFICIAL_SOURCE_SYNC_ENABLED": "true", "OPENSEARCH_SYNC_ENABLED": "true"},
                clear=False,
            ),
            patch.object(knowledge_maintenance, "official_source_due", return_value=True),
            patch.object(knowledge_maintenance.sync_official_sources, "sync", return_value=3) as source_sync,
            patch.object(knowledge_maintenance.sync_opensearch_indices, "synchronize", return_value=12) as index_sync,
        ):
            result = knowledge_maintenance.run_once()

        self.assertEqual(
            {"sourceDue": True, "sourceDocuments": 3, "indexedDocuments": 12},
            result,
        )
        source_sync.assert_called_once_with()
        index_sync.assert_called_once_with()

    def test_skips_source_but_refreshes_derived_index_when_source_is_not_due(self):
        with (
            patch.dict(
                os.environ,
                {"OFFICIAL_SOURCE_SYNC_ENABLED": "true", "OPENSEARCH_SYNC_ENABLED": "true"},
                clear=False,
            ),
            patch.object(knowledge_maintenance, "official_source_due", return_value=False),
            patch.object(knowledge_maintenance.sync_official_sources, "sync") as source_sync,
            patch.object(knowledge_maintenance.sync_opensearch_indices, "synchronize", return_value=7) as index_sync,
        ):
            result = knowledge_maintenance.run_once()

        self.assertEqual(
            {"sourceDue": False, "sourceDocuments": 0, "indexedDocuments": 7},
            result,
        )
        source_sync.assert_not_called()
        index_sync.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
