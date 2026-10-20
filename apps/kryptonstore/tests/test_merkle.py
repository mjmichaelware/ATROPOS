"""Test Suite: Merkle root verification and cryptographic attestation."""
import unittest
from krypton.ledger.merkle_ledger import MerkleLedger
from krypton.models.log_entry import LogEntry

class TestMerkleLedger(unittest.TestCase):
    def test_merkle_root(self):
        e = [LogEntry(1, 1, "P", "k1", b"v1", 100.0), LogEntry(2, 1, "P", "k2", b"v2", 101.0)]
        r1 = MerkleLedger.compute_root(e)
        r2 = MerkleLedger.compute_root(e)
        self.assertEqual(r1, r2)
        self.assertEqual(len(r1), 64)

    def test_tamper(self):
        e1 = [LogEntry(1, 1, "P", "k1", b"v1", 100.0)]
        e2 = [LogEntry(1, 1, "P", "k1", b"v2", 100.0)]
        self.assertNotEqual(MerkleLedger.compute_root(e1), MerkleLedger.compute_root(e2))

if __name__ == "__main__":
    unittest.main()
