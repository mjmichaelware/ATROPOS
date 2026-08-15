"""The closed vocabularies research is defined in terms of.

Evidence types and applicability values. Checked by the write paths that accept
them, so they belong to none of those paths.
"""

from __future__ import annotations

EVIDENCE_TYPES = {
    "OFFICIAL_DOCUMENTATION",
    "PRIMARY_SOURCE",
    "RESEARCH_PAPER",
    "STANDARD",
    "LEGAL_AUTHORITY",
    "SOURCE_CODE",
    "TEST_RESULT",
    "USER_DECISION",
    "OTHER",
}


APPLICABILITY = {
    "APPLICABLE",
    "NOT_APPLICABLE",
}
