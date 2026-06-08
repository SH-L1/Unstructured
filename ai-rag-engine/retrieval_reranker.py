"""Deterministic retrieval reranking utilities for governed evidence.

The production retriever may be OpenSearch hybrid search, but evaluation and
offline audits still need a repeatable reranker that can run without external
services. This module keeps the ranking logic conservative: it rewards lexical
query overlap, purpose fit, verified official legal evidence, source freshness,
and existing retriever score without allowing any single weak signal to dominate.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from datetime import date
from typing import Iterable


TOKEN_RE = re.compile(r"[0-9A-Za-z가-힣_]+")

PURPOSE_WEIGHTS = {
    "OFFICIAL_LAW": 1.0,
    "PROCEDURE": 0.72,
    "LOCAL_ORDINANCE_REFERENCE": 0.58,
    "HISTORICAL_CASE": 0.42,
    "STYLE_REFERENCE": 0.18,
    "STYLE": 0.18,
    "EVALUATION_TRAINING": 0.08,
}

STATUS_WEIGHTS = {
    "VERIFIED_OFFICIAL": 1.0,
    "VERIFIED_INTERNAL": 0.74,
    "SYNTHETIC_DEMO": 0.2,
}


@dataclass(frozen=True)
class RetrievalCandidate:
    evidence_id: str
    title: str = ""
    content: str = ""
    purpose: str = ""
    verification_status: str = ""
    jurisdiction_code: str = ""
    retriever_score: float = 0.0
    effective_from: date | None = None
    effective_to: date | None = None


def tokens(text: str) -> set[str]:
    return {match.group(0).casefold() for match in TOKEN_RE.finditer(text or "")}


def lexical_overlap(query: str, candidate: RetrievalCandidate) -> float:
    query_tokens = tokens(query)
    if not query_tokens:
        return 0.0
    candidate_tokens = tokens(" ".join((candidate.title, candidate.content)))
    if not candidate_tokens:
        return 0.0
    return len(query_tokens & candidate_tokens) / len(query_tokens)


def freshness_score(candidate: RetrievalCandidate, on_date: date | None = None) -> float:
    today = on_date or date.today()
    if candidate.effective_from and candidate.effective_from > today:
        return -1.0
    if candidate.effective_to and candidate.effective_to < today:
        return -1.0
    if not candidate.effective_from:
        return 0.35
    age_days = max((today - candidate.effective_from).days, 0)
    return max(0.25, 1.0 - min(age_days, 3650) / 3650)


def normalized_retriever_score(value: float) -> float:
    if value <= 0:
        return 0.0
    return math.log1p(value) / (1.0 + math.log1p(value))


def candidate_score(
    query: str,
    candidate: RetrievalCandidate,
    expected_purpose: str | None = None,
    on_date: date | None = None,
) -> float:
    purpose = candidate.purpose.upper()
    status = candidate.verification_status.upper()
    expected = (expected_purpose or "").upper()
    purpose_fit = PURPOSE_WEIGHTS.get(purpose, 0.05)
    if expected and purpose != expected:
        purpose_fit *= 0.55
    status_fit = STATUS_WEIGHTS.get(status, 0.05)
    official_law_gate = 0.0
    if purpose == "OFFICIAL_LAW" and status == "VERIFIED_OFFICIAL" and candidate.jurisdiction_code.upper() == "NATIONAL":
        official_law_gate = 1.0
    return (
        0.38 * lexical_overlap(query, candidate)
        + 0.22 * purpose_fit
        + 0.16 * status_fit
        + 0.10 * official_law_gate
        + 0.09 * freshness_score(candidate, on_date)
        + 0.05 * normalized_retriever_score(candidate.retriever_score)
    )


def rerank_candidates(
    query: str,
    candidates: Iterable[RetrievalCandidate],
    expected_purpose: str | None = None,
    top_k: int | None = None,
    on_date: date | None = None,
) -> list[RetrievalCandidate]:
    ranked = sorted(
        candidates,
        key=lambda candidate: (
            candidate_score(query, candidate, expected_purpose, on_date),
            candidate.evidence_id,
        ),
        reverse=True,
    )
    return ranked[:top_k] if top_k is not None else ranked
