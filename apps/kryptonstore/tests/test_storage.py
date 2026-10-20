"""Test Suite: LSM/MemTable storage and tombstone deletion."""
import unittest, tempfile, shutil
from pathlib import Path
from krypton.storage.memtable import MemTable
from krypton.storage.engine import LSMStorageEngine

class TestStorage(unittest.TestCase):
    def setUp(self):
        self.d = Path(tempfile.mkdtemp(prefix="krypton_test_storage_"))
        self.eng = LSMStorageEngine(self.d, 64)

    def tearDown(self):
        shutil.rmtree(self.d, ignore_errors=True)

    def test_memtable(self):
        m = MemTable(128)
        m.put("a", b"b")
        self.assertEqual(m.get("a"), b"b")
        m.delete("a")
        self.assertIsNone(m.get("a"))

    def test_engine_flush(self):
        self.eng.put("x", b"y")
        self.assertEqual(self.eng.get("x"), b"y")
        self.eng.flush()
        self.assertEqual(self.eng.get("x"), b"y")
        self.eng.delete("x")
        self.assertIsNone(self.eng.get("x"))

if __name__ == "__main__":
    unittest.main()
