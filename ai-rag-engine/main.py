"""Compatibility entry point for the restricted support worker.

The former standalone pipeline could classify, retrieve, and generate output
outside the authoritative Spring workflow. That path is intentionally removed.
"""

from worker import run_forever


if __name__ == "__main__":
    run_forever()
