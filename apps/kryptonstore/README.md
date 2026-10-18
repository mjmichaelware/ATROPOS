# KryptonStore

Production-grade, strictly typed Python distributed Raft-consensus Key-Value store.

```
       +------------------------------------------------+
       |             Client Request / CLI               |
       +-----------------------+------------------------+
                               |
                               v
       +-----------------------+------------------------+
       |       Transport Layer (HTTP / Peering)         |
       +-----------------------+------------------------+
                               |
                               v
       +-----------------------+------------------------+
       |    Consensus State Machine (Raft Engine)       |
       +-----------+------------------------+-----------+
                   |                        |
                   v                        v
       +-----------+------------+   +-------+-----------+
       |   WAL Engine (CRC32)   |   | LSM Storage Engine|
       +-----------+------------+   +-------------------+
                   |
                   v
       +-----------+------------+
       |  Cryptographic Ledger  |
       |     (Merkle Tree)      |
       +------------------------+
```

## Features
- **Strictly Typed Python 3.9+**: 100% type hints across 20 discrete modules.
- **Binary Frame WAL**: Strict CRC32 verification with corrupted frame recovery.
- **LSM/MemTable Storage**: Memory-sorted write buffer with tombstones and SSTable flushes.
- **Raft Consensus**: Deterministic state machine protecting against split-brain states.
- **Cryptographic State Attestation**: SHA-256 Merkle tree verification over committed logs.
- **Zero Circular Imports**: Clean dependency graph across all 10 architectural layers.
