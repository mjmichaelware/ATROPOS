/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import java.nio.file.Files
import java.nio.file.Path

/**
 * Synthesizes production-grade WAL Engine and LSM/MemTable Storage modules for KryptonStore.
 */
object KryptonStorageSynthesizer {

    fun synthesize(payload: String, worktreeRoot: Path): SynthesisResult {
        val targetPath = worktreeRoot.resolve(payload)
        targetPath.parent?.let { Files.createDirectories(it) }

        val code = when (payload) {
            "krypton/wal/frame.py" -> walFrameCode()
            "krypton/wal/segment.py" -> walSegmentCode()
            "krypton/storage/memtable.py" -> storageMemtableCode()
            "krypton/storage/engine.py" -> storageEngineCode()
            else -> "# Storage: $payload\n"
        }

        Files.writeString(targetPath, code)
        return SynthesisResult(true, "synthesized $payload")
    }

    private fun walFrameCode(): String = """\"\"\"WAL Engine: Binary frame serializer/deserializer with CRC32 verification.\"\"\"
import struct
import zlib
import json
from typing import Tuple
from krypton.models.log_entry import LogEntry

class FrameCorruptionError(Exception): pass

class FrameCodec:
    HEADER_FORMAT = ">II"
    HEADER_SIZE = struct.calcsize(HEADER_FORMAT)

    @classmethod
    def serialize(cls, entry: LogEntry) -> bytes:
        payload = json.dumps({
            "index": entry.index,
            "term": entry.term,
            "command": entry.command,
            "key": entry.key,
            "value": entry.value.hex(),
            "timestamp": entry.timestamp,
        }, sort_keys=True).encode("utf-8")
        checksum = zlib.crc32(payload) & 0xFFFFFFFF
        header = struct.pack(cls.HEADER_FORMAT, len(payload), checksum)
        return header + payload

    @classmethod
    def deserialize(cls, buffer: bytes, offset: int = 0) -> Tuple[LogEntry, int]:
        if len(buffer) - offset < cls.HEADER_SIZE:
            raise ValueError("Buffer too short for header")
        length, expected_crc = struct.unpack_from(cls.HEADER_FORMAT, buffer, offset)
        start = offset + cls.HEADER_SIZE
        end = start + length
        if len(buffer) < end:
            raise ValueError("Truncated payload")
        payload = buffer[start:end]
        if (zlib.crc32(payload) & 0xFFFFFFFF) != expected_crc:
            raise FrameCorruptionError("CRC32 mismatch on frame")
        data = json.loads(payload.decode("utf-8"))
        return LogEntry(
            index=int(data["index"]),
            term=int(data["term"]),
            command=str(data["command"]),
            key=str(data["key"]),
            value=bytes.fromhex(data["value"]),
            timestamp=float(data.get("timestamp", 0.0)),
        ), end
"""

    private fun walSegmentCode(): String = """\"\"\"WAL Engine: Rolling segment files with corruption recovery.\"\"\"
import os
from pathlib import Path
from typing import List
from krypton.models.log_entry import LogEntry
from krypton.wal.frame import FrameCodec, FrameCorruptionError

class WALSegmentManager:
    def __init__(self, wal_dir: Path, max_segment_bytes: int = 1024 * 1024) -> None:
        self.wal_dir = Path(wal_dir)
        self.wal_dir.mkdir(parents=True, exist_ok=True)
        self.max_segment_bytes = max_segment_bytes
        self.active_segment_id = 0
        self._entries: List[LogEntry] = []
        self._roll_if_needed()

    def _segment_path(self, seg_id: int) -> Path:
        return self.wal_dir / f"wal_{seg_id:06d}.log"

    def _roll_if_needed(self) -> None:
        p = self._segment_path(self.active_segment_id)
        if p.exists() and p.stat().st_size >= self.max_segment_bytes:
            self.active_segment_id += 1

    def append(self, entry: LogEntry) -> int:
        self._roll_if_needed()
        frame = FrameCodec.serialize(entry)
        path = self._segment_path(self.active_segment_id)
        with open(path, "ab") as f:
            f.write(frame)
            f.flush()
            os.fsync(f.fileno())
        self._entries.append(entry)
        return entry.index

    def read_all(self) -> List[LogEntry]:
        return list(self._entries)

    def recover(self) -> List[LogEntry]:
        recovered: List[LogEntry] = []
        for seg in sorted(self.wal_dir.glob("wal_*.log")):
            data = seg.read_bytes()
            offset = 0
            while offset < len(data):
                try:
                    entry, next_offset = FrameCodec.deserialize(data, offset)
                    recovered.append(entry)
                    offset = next_offset
                except (FrameCorruptionError, ValueError):
                    break
        self._entries = recovered
        return list(self._entries)

    def close(self) -> None: pass
"""

    private fun storageMemtableCode(): String = """\"\"\"LSM/MemTable Storage: In-memory sorted map with flush thresholds and tombstones.\"\"\"
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
"""

    private fun storageEngineCode(): String = """\"\"\"LSM Storage Engine implementation.\"\"\"
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
        content = "".join(f"{k}\t{('__TOMBSTONE__' if v is None else v.hex())}\n" for k, v in sorted(self._disk_cache.items()))
        self.sstable_file.write_text(content, encoding="utf-8")
        self.memtable.clear()
"""
}
