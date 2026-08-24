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

    @Test
    fun production_write_decision_can_fail_closed_with_evidence() {
        val guard = FactoryProgressGuard(DagStore(Files.createTempDirectory("factory-write")))
        guard.observeWriteOrThrow("atom-1", listOf("a"))
        guard.observeWriteOrThrow("atom-1", listOf("b"))
        val failure = runCatching { guard.observeWriteOrThrow("atom-1", listOf("a")) }.exceptionOrNull()
        assertTrue(failure?.message?.contains("evidence_sha256=") == true)
    }

    @Test
    fun thrash_state_survives_a_new_guard_after_process_death() {
        val root = Files.createTempDirectory("factory-progress-durable")
        val store = DagStore(root)
        val dag = store.createDag("progress", listOf(node("atom-1")), "run-1")
        FactoryProgressGuard(store, identicalFailureLimit = 3).also { guard ->
            assertTrue(guard.observeFailure(dag.id, "atom-1", "same failure").allowed)
            assertTrue(guard.observeFailure(dag.id, "atom-1", "same failure").allowed)
        }
        val afterRestart = FactoryProgressGuard(store, identicalFailureLimit = 3)
        assertFalse(afterRestart.observeFailure(dag.id, "atom-1", "same failure").allowed)
        assertTrue(store.readDag(dag.id)!!.nodes.single().state == DagNodeState.BLOCKED)
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
