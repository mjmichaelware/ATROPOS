"""Test Suite: WAL corruption recovery and CRC32 verification."""
import unittest, tempfile, shutil
from pathlib import Path
from krypton.models.log_entry import LogEntry
from krypton.wal.frame import FrameCodec, FrameCorruptionError
from krypton.wal.segment import WALSegmentManager

class TestWALEngine(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp(prefix="krypton_test_wal_"))
        self.wal = WALSegmentManager(self.d, max_segment_bytes=512)

    def tearDown(self):
        shutil.rmtree(self.d, ignore_errors=True)

    def test_frame_serialization_crc32(self):
        e = LogEntry(1, 1, "SET", "alpha", b"beta")
        enc = FrameCodec.serialize(e)
        dec, _ = FrameCodec.deserialize(enc)
        self.assertEqual(dec.key, "alpha")
        self.assertEqual(dec.value, b"beta")

    def test_crc32_corruption_detection(self):
        e = LogEntry(2, 1, "SET", "secure", b"payload")
        b = bytearray(FrameCodec.serialize(e))
        b[-1] ^= 0xFF
        with self.assertRaises(FrameCorruptionError):
            FrameCodec.deserialize(bytes(b))

    def test_wal_recovery(self):
        self.wal.append(LogEntry(1, 1, "PUT", "k1", b"v1"))
        self.wal.append(LogEntry(2, 1, "PUT", "k2", b"v2"))
        seg = sorted(self.d.glob("wal_*.log"))[0]
        with open(seg, "ab") as f:
            f.write(b"CORRUPTED_BYTES")
        rec = self.wal.recover()
        self.assertEqual(len(rec), 2)

if __name__ == "__main__":
    unittest.main()
