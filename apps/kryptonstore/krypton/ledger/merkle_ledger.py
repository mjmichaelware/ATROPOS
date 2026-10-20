"""Cryptographic Ledger: Merkle tree calculation over committed log entries."""
import hashlib
from typing import List
from krypton.models.log_entry import LogEntry

class MerkleLedger:
    @staticmethod
    def hash_leaf(entry: LogEntry) -> str:
        payload = f"{entry.index}:{entry.term}:{entry.command}:{entry.key}:{entry.value.hex()}:{entry.timestamp}"
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    @staticmethod
    def hash_pair(left: str, right: str) -> str:
        comb = left + right if left <= right else right + left
        return hashlib.sha256(comb.encode("utf-8")).hexdigest()

    @classmethod
    def compute_root(cls, entries: List[LogEntry]) -> str:
        if not entries: return hashlib.sha256(b"EMPTY_LEDGER").hexdigest()
        curr = [cls.hash_leaf(e) for e in sorted(entries, key=lambda x: x.index)]
        while len(curr) > 1:
            nxt: List[str] = []
            for i in range(0, len(curr), 2):
                if i + 1 < len(curr): nxt.append(cls.hash_pair(curr[i], curr[i + 1]))
                else: nxt.append(cls.hash_pair(curr[i], curr[i]))
            curr = nxt
        return curr[0]

    @classmethod
    def verify_entry_presence(cls, entry: LogEntry, proof: List[str], expected_root: str) -> bool:
        curr = cls.hash_leaf(entry)
        for s in proof: curr = cls.hash_pair(curr, s)
        return curr == expected_root
