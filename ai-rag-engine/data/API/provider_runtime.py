"""Common bounded runtime for AI and public API providers."""

from __future__ import annotations

import os
import threading
import time
from dataclasses import dataclass
from typing import Callable, Generic, TypeVar


T = TypeVar("T")


class ProviderUnavailableError(RuntimeError):
    pass


@dataclass(frozen=True)
class ProviderExecution(Generic[T]):
    value: T
    retries: int
    duration_ms: int
    cost_units: int


@dataclass
class _CircuitState:
    failures: int = 0
    opened_until: float = 0.0


_states: dict[str, _CircuitState] = {}
_lock = threading.Lock()


def execute_provider_call(
    provider: str,
    cost_units: int,
    call: Callable[[], T],
) -> ProviderExecution[T]:
    provider_name = provider.strip().lower()
    if not provider_name:
        raise ValueError("Provider name is required")
    if cost_units < 0:
        raise ValueError("Provider cost units must be non-negative")
    key = _env_key(provider_name)
    max_cost = _int_setting(key, "MAX_COST_UNITS", "PROVIDER_MAX_COST_UNITS_PER_CALL", 2000)
    if cost_units > max_cost:
        raise ProviderUnavailableError(
            f"{provider_name} call cost {cost_units} exceeds configured maximum {max_cost}"
        )
    max_attempts = max(1, _int_setting(key, "MAX_ATTEMPTS", "PROVIDER_MAX_ATTEMPTS", 3))
    failure_threshold = max(
        1,
        _int_setting(key, "CIRCUIT_FAILURE_THRESHOLD", "PROVIDER_CIRCUIT_FAILURE_THRESHOLD", 3),
    )
    open_seconds = max(
        1.0,
        _float_setting(key, "CIRCUIT_OPEN_SECONDS", "PROVIDER_CIRCUIT_OPEN_SECONDS", 60.0),
    )
    retry_base = max(
        0.0,
        _float_setting(key, "RETRY_BASE_SECONDS", "PROVIDER_RETRY_BASE_SECONDS", 1.0),
    )
    started_at = time.monotonic()
    _require_closed(provider_name)
    for attempt in range(1, max_attempts + 1):
        try:
            value = call()
            _record_success(provider_name)
            return ProviderExecution(
                value=value,
                retries=attempt - 1,
                duration_ms=int((time.monotonic() - started_at) * 1000),
                cost_units=cost_units,
            )
        except Exception:
            opened = _record_failure(provider_name, failure_threshold, open_seconds)
            if attempt >= max_attempts or opened:
                raise
            time.sleep(min(30.0, retry_base * (2 ** (attempt - 1))))
    raise ProviderUnavailableError(f"{provider_name} call exhausted its retry budget")


def reset_provider_state(provider: str | None = None) -> None:
    with _lock:
        if provider is None:
            _states.clear()
        else:
            _states.pop(provider.strip().lower(), None)


def _require_closed(provider: str) -> None:
    with _lock:
        state = _states.setdefault(provider, _CircuitState())
        if state.opened_until > time.monotonic():
            raise ProviderUnavailableError(f"{provider} circuit is open")
        if state.opened_until:
            state.failures = 0
            state.opened_until = 0.0


def _record_success(provider: str) -> None:
    with _lock:
        _states[provider] = _CircuitState()


def _record_failure(provider: str, threshold: int, open_seconds: float) -> bool:
    with _lock:
        state = _states.setdefault(provider, _CircuitState())
        state.failures += 1
        if state.failures >= threshold:
            state.opened_until = time.monotonic() + open_seconds
            return True
        return False


def _env_key(provider: str) -> str:
    return "".join(character if character.isalnum() else "_" for character in provider.upper())


def _int_setting(provider_key: str, suffix: str, fallback: str, default: int) -> int:
    return int(os.getenv(f"PROVIDER_{provider_key}_{suffix}", os.getenv(fallback, str(default))))


def _float_setting(provider_key: str, suffix: str, fallback: str, default: float) -> float:
    return float(os.getenv(f"PROVIDER_{provider_key}_{suffix}", os.getenv(fallback, str(default))))
