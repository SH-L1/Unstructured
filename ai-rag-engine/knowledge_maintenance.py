"""Run due official-source and derived OpenSearch maintenance.

This process is operational glue, not a workflow authority. It checks the
governed source schedule, synchronizes official sources only when due, and
refreshes the derived search indices after a successful source sync.
"""

from __future__ import annotations

import os
import time
from datetime import datetime

import psycopg2
from dotenv import load_dotenv

import sync_official_sources
import sync_opensearch_indices


load_dotenv()


def official_source_due(now: datetime | None = None) -> bool:
    current = now or datetime.now()
    with psycopg2.connect(**sync_official_sources.connection_kwargs()) as connection:
        connection.autocommit = True
        with connection.cursor() as cursor:
            cursor.execute(
                """
                select status, next_sync_at, stale_after
                from source_registry
                where name = %s
                """,
                (sync_official_sources.SOURCE_NAME,),
            )
            row = cursor.fetchone()
    if row is None:
        return True
    status, next_sync_at, stale_after = row
    return (
        status != "ACTIVE"
        or next_sync_at is None
        or next_sync_at <= current
        or stale_after is None
        or stale_after <= current
    )


def run_once() -> dict[str, int | bool]:
    source_enabled = os.getenv("OFFICIAL_SOURCE_SYNC_ENABLED", "false").lower() == "true"
    search_enabled = os.getenv("OPENSEARCH_SYNC_ENABLED", "false").lower() == "true"
    result: dict[str, int | bool] = {
        "sourceDue": False,
        "sourceDocuments": 0,
        "indexedDocuments": 0,
    }
    if source_enabled:
        result["sourceDue"] = official_source_due()
        if result["sourceDue"]:
            result["sourceDocuments"] = sync_official_sources.sync()
    if search_enabled:
        result["indexedDocuments"] = sync_opensearch_indices.synchronize()
    return result


def run_forever() -> None:
    poll_seconds = max(1.0, float(os.getenv("MAINTENANCE_POLL_SECONDS", "300")))
    failure_base_seconds = max(1.0, float(os.getenv("MAINTENANCE_FAILURE_BASE_SECONDS", "30")))
    failure_max_seconds = max(
        failure_base_seconds,
        float(os.getenv("MAINTENANCE_FAILURE_MAX_SECONDS", "1800")),
    )
    consecutive_failures = 0
    while True:
        try:
            print(run_once(), flush=True)
            consecutive_failures = 0
            time.sleep(poll_seconds)
        except Exception as exception:
            consecutive_failures += 1
            print(f"Knowledge maintenance failed: {exception}", flush=True)
            delay = min(
                failure_max_seconds,
                failure_base_seconds * (2 ** max(0, consecutive_failures - 1)),
            )
            time.sleep(delay)


if __name__ == "__main__":
    run_forever()
