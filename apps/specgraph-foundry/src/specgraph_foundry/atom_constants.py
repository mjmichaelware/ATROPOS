"""Constants shared across atomization.

The extractor version in particular is a contract, not a detail: it is stored on
every extraction so a later reader can tell which algorithm produced an atom.
"""

from __future__ import annotations

EXTRACTOR_VERSION = "deterministic-atomizer-1"
