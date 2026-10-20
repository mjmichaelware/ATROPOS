"""LSM Storage Engine implementation."""
from pathlib import Path
from typing import Optional
from krypton.storage.memtable import MemTable

class LSMStorageEngine:
    def __init__(self, data_dir: Path, flush_threshold_bytes: int = 64 * 1024) -> None:
        self.data_dir = Path(data_dir)
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.memtable = MemTable(flush_threshold_bytes)
        self.sstable_file = self.data_dir / "sstable_0.sst"
        self._disk_cache = {}
        self._load_sstable()

    def _load_sstable(self) -> None:
        if not self.sstable_file.exists(): return
        for line in self.sstable_file.read_text(encoding="utf-8").splitlines():
            if "\t" in line:
                k, v = line.split("\t", 1)
                self._disk_cache[k] = None if v == "__TOMBSTONE__" else bytes.fromhex(v)

    def get(self, key: str) -> Optional[bytes]:
        val = self.memtable.get(key)
        if val is not None: return val
        if key in self.memtable._data and self.memtable._data[key] is None: return None
        return self._disk_cache.get(key)

    def put(self, key: str, value: bytes) -> None:
        self.memtable.put(key, value)
        if self.memtable.should_flush(): self.flush()

    def delete(self, key: str) -> None:
        self.memtable.delete(key)
        if self.memtable.should_flush(): self.flush()

    def flush(self) -> None:
        for k, v in self.memtable.items_sorted():
            self._disk_cache[k] = v
        lines = []
        for k, v in sorted(self._disk_cache.items()):
            val_str = "__TOMBSTONE__" if v is None else v.hex()
            lines.append(k + "\\t" + val_str)
        content = "\\n".join(lines) + ("\\n" if lines else "")
        self.sstable_file.write_text(content, encoding="utf-8")
        self.memtable.clear()
