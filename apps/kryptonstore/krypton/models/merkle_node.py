"""Core models: MerkleNode."""
from dataclasses import dataclass
from typing import Optional

@dataclass
class MerkleNode:
    hash_value: str
    left_hash: Optional[str] = None
    right_hash: Optional[str] = None
    data_hash: Optional[str] = None
