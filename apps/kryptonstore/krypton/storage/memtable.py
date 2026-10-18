\"\"\"LSM/MemTable Storage: In-memory sorted map with flush thresholds and tombstones.\"\"\"
from typing import Dict, List, Optional, Tuple

class MemTable:
    TOMBSTONE = None

    def __init__(self, flush_threshold_bytes: int = 64 * 1024) -> None:
        self._data: Dict[str, Optional[bytes]] = {}
        self.flush_threshold_bytes = flush_threshold_bytes
        self._approx_bytes = 0

    def put(self, key: str, value: bytes) -> None:
        old = self._data.get(key)
        self._data[key] = value
        self._approx_bytes += len(key) + len(value)
        if old is not None:
            self._approx_bytes -= (len(key) + len(old))

    def get(self, key: str) -> Optional[bytes]:
        val = self._data.get(key)
        return None if val is self.TOMBSTONE else val

    def delete(self, key: str) -> None:
        self.memtable_del(key)

    def memtable_del(self, key: str) -> None:
        self._data[key] = self.TOMBSTONE
        self._approx_bytes += len(key)

    def should_flush(self) -> bool:
        return self._approx_bytes >= self.flush_threshold_bytes

    def items_sorted(self) -> List[Tuple[str, Optional[bytes]]]:
        return sorted(self._data.items(), key=lambda x: x[0])

    def clear(self) -> None:
        self._data.clear()
        self._approx_bytes = 0
