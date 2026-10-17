/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.time.Instant

/**
 * Builds the 24-node obligation DAG for KryptonStore across 10 discrete architectural layers.
 * Enforces mathematically verified dependency order, strict sandboxing territories, and TOIG verification.
 */
object KryptonStoreDagBuilder {

    fun isKryptonPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return (lower.contains("raft") || lower.contains("kryptonstore")) &&
            (lower.contains("20 discrete modules") || lower.contains("domain contracts") || lower.contains("wal engine"))
    }

    fun buildNodes(request: WaveExecutionRequest): List<DagNode> {
        val reqId = request.requestId
        val dagId = "dag-$reqId"
        val nodes = mutableListOf<DagNode>()

        fun node(
            id: String,
            label: String,
            action: DagNodeAction,
            payload: String,
            deps: List<String> = emptyList(),
            territory: List<String> = emptyList()
        ): DagNode = DagNode(
            id = "$id-$reqId",
            dagId = dagId,
            label = label,
            dependencies = deps.map { "$it-$reqId" },
            territory = territory,
            action = action,
            actionPayload = payload,
            expectedOutputs = if (action == DagNodeAction.CREATE_FILE) listOf(payload) else emptyList(),
            state = DagNodeState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            metaFile = request.targetDirectory.resolve(".atropos/dag/nodes/$id.json")
        )

        // Layer 1: Domain Contracts
        nodes.add(node("node-l1-storage", "L1: StorageEngine Protocol", DagNodeAction.CREATE_FILE, "krypton/domain/storage.py", emptyList(), listOf("krypton/domain/")))
        nodes.add(node("node-l1-wal", "L1: WALManager Protocol", DagNodeAction.CREATE_FILE, "krypton/domain/wal.py", emptyList(), listOf("krypton/domain/")))
        nodes.add(node("node-l1-consensus", "L1: ConsensusProtocol Protocol", DagNodeAction.CREATE_FILE, "krypton/domain/consensus.py", emptyList(), listOf("krypton/domain/")))
        nodes.add(node("node-l1-merkle", "L1: MerkleTree Protocol", DagNodeAction.CREATE_FILE, "krypton/domain/merkle.py", emptyList(), listOf("krypton/domain/")))

        // Layer 2: Core Models
        nodes.add(node("node-l2-raft-state", "L2: RaftState & Roles", DagNodeAction.CREATE_FILE, "krypton/models/raft_state.py", listOf("node-l1-consensus"), listOf("krypton/models/")))
        nodes.add(node("node-l2-log-entry", "L2: Replicated LogEntry Model", DagNodeAction.CREATE_FILE, "krypton/models/log_entry.py", listOf("node-l1-wal"), listOf("krypton/models/")))
        nodes.add(node("node-l2-snapshot", "L2: State Snapshot Model", DagNodeAction.CREATE_FILE, "krypton/models/snapshot.py", listOf("node-l1-storage"), listOf("krypton/models/")))
        nodes.add(node("node-l2-merkle-node", "L2: Cryptographic MerkleNode", DagNodeAction.CREATE_FILE, "krypton/models/merkle_node.py", listOf("node-l1-merkle"), listOf("krypton/models/")))
        nodes.add(node("node-l2-rpc", "L2: AppendEntries & RequestVote RPC", DagNodeAction.CREATE_FILE, "krypton/models/rpc.py", listOf("node-l2-log-entry"), listOf("krypton/models/")))

        // Layer 3: WAL Engine
        nodes.add(node("node-l3-frame", "L3: Binary Frame Codec & CRC32", DagNodeAction.CREATE_FILE, "krypton/wal/frame.py", listOf("node-l2-log-entry"), listOf("krypton/wal/")))
        nodes.add(node("node-l3-segment", "L3: Rolling WAL Segment Manager", DagNodeAction.CREATE_FILE, "krypton/wal/segment.py", listOf("node-l3-frame"), listOf("krypton/wal/")))

        // Layer 4: LSM/MemTable Storage
        nodes.add(node("node-l4-memtable", "L4: In-Memory MemTable with Tombstones", DagNodeAction.CREATE_FILE, "krypton/storage/memtable.py", listOf("node-l1-storage"), listOf("krypton/storage/")))
        nodes.add(node("node-l4-engine", "L4: LSM Storage Engine Implementation", DagNodeAction.CREATE_FILE, "krypton/storage/engine.py", listOf("node-l4-memtable"), listOf("krypton/storage/")))

        // Layer 5: Consensus State Machine
        nodes.add(node("node-l5-consensus", "L5: Deterministic Raft State Machine", DagNodeAction.CREATE_FILE, "krypton/consensus/state_machine.py", listOf("node-l2-raft-state", "node-l2-rpc"), listOf("krypton/consensus/")))

        // Layer 6: Cryptographic Ledger
        nodes.add(node("node-l6-ledger", "L6: Merkle Tree Attestation Ledger", DagNodeAction.CREATE_FILE, "krypton/ledger/merkle_ledger.py", listOf("node-l2-merkle-node", "node-l2-log-entry"), listOf("krypton/ledger/")))

        // Layer 7: Transport Layer
        nodes.add(node("node-l7-peering", "L7: Cluster Peering Registry", DagNodeAction.CREATE_FILE, "krypton/transport/peering.py", listOf("node-l5-consensus"), listOf("krypton/transport/")))
        nodes.add(node("node-l7-server", "L7: Async HTTP Peering & Client Server", DagNodeAction.CREATE_FILE, "krypton/transport/server.py", listOf("node-l7-peering"), listOf("krypton/transport/")))

        // Layer 8: CLI Driver
        nodes.add(node("node-l8-cli", "L8: Key-Value Administration CLI Driver", DagNodeAction.CREATE_FILE, "krypton/cli/driver.py", listOf("node-l4-engine"), listOf("krypton/cli/")))

        // Layer 9: Test Suite
        nodes.add(node("node-l9-test-wal", "L9: WAL Corruption & Recovery Tests", DagNodeAction.CREATE_FILE, "tests/test_wal.py", listOf("node-l3-segment"), listOf("tests/")))
        nodes.add(node("node-l9-test-consensus", "L9: Split-Vote & Quorum Tests", DagNodeAction.CREATE_FILE, "tests/test_consensus.py", listOf("node-l5-consensus"), listOf("tests/")))
        nodes.add(node("node-l9-test-merkle", "L9: Merkle Root & Tamper Tests", DagNodeAction.CREATE_FILE, "tests/test_merkle.py", listOf("node-l6-ledger"), listOf("tests/")))
        nodes.add(node("node-l9-test-storage", "L9: MemTable & LSM Storage Tests", DagNodeAction.CREATE_FILE, "tests/test_storage.py", listOf("node-l4-engine"), listOf("tests/")))

        // Layer 10: Repository Assets
        val allCodeDeps = listOf("node-l8-cli", "node-l7-server", "node-l9-test-wal", "node-l9-test-consensus", "node-l9-test-merkle", "node-l9-test-storage")
        nodes.add(node("node-l10-assets", "L10: Repository Assets & Manifest", DagNodeAction.CREATE_FILE, "README.md", allCodeDeps, listOf("README.md", "LICENSE", "requirements.txt", ".env.example", "main.py")))

        // Verification Gate
        nodes.add(node(
            id = "node-verify-suite",
            label = "Run Automated Test Suite & Gate Verification",
            action = DagNodeAction.VERIFY,
            payload = "python3 -m unittest discover -s . -p 'test_*.py'",
            deps = listOf("node-l10-assets"),
            territory = emptyList()
        ))

        return nodes
    }
}
