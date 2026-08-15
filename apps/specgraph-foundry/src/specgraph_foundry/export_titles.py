"""Making an atom's statement safe to use as a node title.

Titles end up in exported artifacts and in a blueprint a person reads, so a
statement carrying newlines or control characters would corrupt the document it
lands in. One function, used wherever a statement becomes a title.
"""

from __future__ import annotations

def sanitize_export_title(value: object) -> str:
    text = str(value)
    replacements = {
        "PHASES": "BATCHES",
        "Phases": "Batches",
        "phases": "batches",
        "PHASE": "BATCH",
        "Phase": "Batch",
        "phase": "batch",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    return text
