"""WAL Engine: Rolling segment files with corruption recovery."""
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
