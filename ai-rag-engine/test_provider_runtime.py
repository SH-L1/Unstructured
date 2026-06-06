import os
import unittest
from unittest.mock import patch

from data.API.provider_runtime import (
    ProviderUnavailableError,
    execute_provider_call,
    reset_provider_state,
)


class ProviderRuntimeTest(unittest.TestCase):
    def setUp(self):
        reset_provider_state()

    def test_retries_with_independent_provider_state(self):
        attempts = {"count": 0}

        def flaky():
            attempts["count"] += 1
            if attempts["count"] < 3:
                raise RuntimeError("temporary")
            return "ok"

        with patch.dict(
            os.environ,
            {
                "PROVIDER_TEST_MAX_ATTEMPTS": "3",
                "PROVIDER_TEST_RETRY_BASE_SECONDS": "0",
                "PROVIDER_TEST_CIRCUIT_FAILURE_THRESHOLD": "5",
            },
            clear=False,
        ):
            result = execute_provider_call("test", 10, flaky)

        self.assertEqual("ok", result.value)
        self.assertEqual(2, result.retries)

    def test_opens_circuit_and_rejects_excess_cost(self):
        with patch.dict(
            os.environ,
            {
                "PROVIDER_BROKEN_MAX_ATTEMPTS": "1",
                "PROVIDER_BROKEN_CIRCUIT_FAILURE_THRESHOLD": "1",
                "PROVIDER_BROKEN_CIRCUIT_OPEN_SECONDS": "60",
                "PROVIDER_BROKEN_MAX_COST_UNITS": "10",
            },
            clear=False,
        ):
            with self.assertRaises(RuntimeError):
                execute_provider_call("broken", 1, lambda: (_ for _ in ()).throw(RuntimeError("down")))
            with self.assertRaises(ProviderUnavailableError):
                execute_provider_call("broken", 1, lambda: "never")
            with self.assertRaises(ProviderUnavailableError):
                execute_provider_call("broken-cost", 2001, lambda: "never")


if __name__ == "__main__":
    unittest.main()
