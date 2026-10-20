"""Core models: State Snapshot."""
from dataclasses import dataclass
from typing import Dict

@dataclass
class Snapshot:
    last_included_index: int
    last_included_term: int
    state: Dict[str, bytes]
    merkle_root: str
