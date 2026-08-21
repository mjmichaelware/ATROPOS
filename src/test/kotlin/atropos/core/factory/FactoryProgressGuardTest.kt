package atropos.core.factory

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryProgressGuardTest {
    @Test
    fun repeated_identical_failure_blocks_the_atom_in_the_existing_dag() {
        val root = Files.createTempDirectory("factory-progress")
        val store = DagStore(root)
        val dag = store.createDag("progress", listOf(node("atom-1")), "run-1")
        val guard = FactoryProgressGuard(store, identicalFailureLimit = 3)

        assertTrue(guard.observeFailure(dag.id, "atom-1", "compile failed at line 10").allowed)
        assertTrue(guard.observeFailure(dag.id, "atom-1", "compile failed at line 11").allowed)
        val decision = guard.observeFailure(dag.id, "atom-1", "compile failed at line 12")
        assertFalse(decision.allowed)
        assertTrue(store.readDag(dag.id)!!.nodes.single().state == DagNodeState.BLOCKED)
    }

    @Test
    fun alternating_file_fingerprints_are_not_allowed_to_continue() {
        val guard = FactoryProgressGuard(DagStore(Files.createTempDirectory("factory-oscillation")))
        assertTrue(guard.observeWrite("atom-1", listOf("a")).allowed)
        assertTrue(guard.observeWrite("atom-1", listOf("b")).allowed)
        assertFalse(guard.observeWrite("atom-1", listOf("a")).allowed)
    }

    private fun node(id: String): DagNode = DagNode(
        id = id,
        label = id,
        action = DagNodeAction.VERIFY,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        metaFile = Paths.get("placeholder")
    )
}
