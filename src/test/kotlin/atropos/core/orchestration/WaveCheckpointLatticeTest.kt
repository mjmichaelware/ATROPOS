/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaveCheckpointLatticeTest {

    @Test
    fun persists_and_loads_cryptographic_node_checkpoints() {
        val tempDir = createTempDirectory("checkpoint-test")
        try {
            val completedNode = DagNode(
                id = "node-1",
                dagId = "dag-abc",
                label = "Compile core module",
                action = DagNodeAction.EXECUTE_COMMAND,
                actionPayload = "kotlinc",
                state = DagNodeState.COMPLETE
            )

            val checkpointFile = WaveCheckpointLattice.persistCheckpoint(
                repoRoot = tempDir,
                dagId = "dag-abc",
                completedNodes = listOf(completedNode),
                freezeSha256 = "deadbeef12345678"
            )

            assertTrue(Files.isRegularFile(checkpointFile))

            val loaded = WaveCheckpointLattice.loadCheckpoint(tempDir, "dag-abc")
            assertTrue(loaded.containsKey("node-1"))
            assertTrue(WaveCheckpointLattice.isNodeSettled(tempDir, "dag-abc", "node-1"))
            assertFalse(WaveCheckpointLattice.isNodeSettled(tempDir, "dag-abc", "node-2"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun isolates_failing_leaf_sub_dag_and_dependencies() {
        val node1 = DagNode(id = "node-root", dagId = "dag-1", label = "Root", state = DagNodeState.COMPLETE)
        val node2 = DagNode(id = "node-mid", dagId = "dag-1", label = "Mid", dependencies = listOf("node-root"), state = DagNodeState.FAILED)
        val node3 = DagNode(id = "node-verify-suite", dagId = "dag-1", label = "Verify", dependencies = listOf("node-mid"), state = DagNodeState.FAILED)
        val node4 = DagNode(id = "node-other-branch", dagId = "dag-1", label = "Other", dependencies = listOf("node-root"), state = DagNodeState.COMPLETE)

        val dag = DagDefinition(
            id = "dag-1",
            nodes = listOf(node1, node2, node3, node4)
        )

        val subDag = WaveCheckpointLattice.isolateSubDag(dag, "node-verify-suite")
        val subDagIds = subDag.map { it.id }.toSet()

        assertTrue(subDagIds.contains("node-verify-suite"))
        assertTrue(subDagIds.contains("node-mid"))
        assertFalse(subDagIds.contains("node-root")) // already complete, not uncompleted
        assertFalse(subDagIds.contains("node-other-branch"))
    }
}
