"""The relation vocabulary between atoms.

One closed set, alone, because it is a contract shared by the writer that
validates a relation and the synthesizer that orders work from it.
"""

from __future__ import annotations

RELATION_TYPES = {
    "REQUIRES",
    "REFINES",
    "CONFLICTS_WITH",
    "DUPLICATES",
    "IMPLEMENTS",
    "VERIFIES",
    "RELATES_TO",
}
