import os
import unittest
from unittest.mock import patch

import spatial_data_common


class SgisTokenRefreshTest(unittest.TestCase):
    def setUp(self):
        spatial_data_common._sgis_cached_token = None
        spatial_data_common._sgis_cached_token_expires_at = 0.0

    def test_fetches_token_from_consumer_credentials(self):
        calls = []

        def fake_fetch_json(url, api_key=None, key_param="serviceKey"):
            calls.append(url)
            if "authentication.json" in url:
                return {"errCd": "0", "result": {"accessToken": "issued-token", "accessTimeout": "14400"}}
            self.assertIn("accessToken=issued-token", url)
            return {"errCd": "0", "features": []}

        with patch.dict(
            os.environ,
            {
                "SGIS_ACCESS_TOKEN": "",
                "SGIS_CONSUMER_KEY": "consumer-key",
                "SGIS_CONSUMER_SECRET": "consumer-secret",
                "SGIS_AUTH_URL": "https://sgisapi.example/OpenAPI3/auth/authentication.json",
            },
            clear=False,
        ), patch.object(spatial_data_common, "fetch_json", side_effect=fake_fetch_json):
            data = spatial_data_common.fetch_sgis_json("https://sgisapi.example/OpenAPI3/boundary.json")

        self.assertEqual({"errCd": "0", "features": []}, data)
        self.assertEqual(2, len(calls))

    def test_refreshes_token_once_when_sgis_returns_expired_token(self):
        calls = []

        def fake_fetch_json(url, api_key=None, key_param="serviceKey"):
            calls.append(url)
            if "authentication.json" in url:
                return {"errCd": "0", "result": {"accessToken": "refreshed-token", "accessTimeout": "14400"}}
            if "accessToken=stale-token" in url:
                return {"errCd": "-401", "errMsg": "expired"}
            self.assertIn("accessToken=refreshed-token", url)
            return {"errCd": "0", "features": [{"type": "Feature"}]}

        with patch.dict(
            os.environ,
            {
                "SGIS_ACCESS_TOKEN": "stale-token",
                "SGIS_CONSUMER_KEY": "consumer-key",
                "SGIS_CONSUMER_SECRET": "consumer-secret",
                "SGIS_AUTH_URL": "https://sgisapi.example/OpenAPI3/auth/authentication.json",
            },
            clear=False,
        ), patch.object(spatial_data_common, "fetch_json", side_effect=fake_fetch_json):
            data = spatial_data_common.fetch_sgis_json("https://sgisapi.example/OpenAPI3/boundary.json?accessToken=old")

        self.assertEqual("0", data["errCd"])
        self.assertEqual(3, len(calls))


if __name__ == "__main__":
    unittest.main()
