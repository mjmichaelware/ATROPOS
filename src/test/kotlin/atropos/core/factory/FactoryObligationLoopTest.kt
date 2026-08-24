package atropos.core.factory

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FactoryObligationLoopTest {
    @Test
    fun outer_loop_refuses_wave_that_executes_no_atoms() {
        val root = Files.createTempDirectory("factory-loop")
        val store = DagStore(root)
        val dag = store.createDag("calculator", listOf(node("requirements")), "run-1")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), dag.nodes.map { it.id }, "CLI@1-1")

        assertFailsWith<IllegalArgumentException> {
            FactoryObligationLoop(store).executeUntilSettled(dag.id, freeze) { emptySet() }
        }
        assertTrue(store.readDag(dag.id)!!.nodes.none { it.state == DagNodeState.COMPLETE })
    }

    @Test
    fun outer_loop_executes_dependency_waves_until_open_work_is_empty() {
        val root = Files.createTempDirectory("factory-outer-loop")
        val store = DagStore(root)
        val dag = store.createDag("waves", listOf(node("a"), node("b", listOf("a")), node("c", listOf("b"))), "run")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), dag.nodes.map { it.id }, "CLI@1-1")
        val seen = mutableListOf<List<String>>()
        val result = FactoryObligationLoop(store).executeUntilSettled(dag.id, freeze) { ready ->
            seen += ready.map { it.id }
            ready.map { it.id }.toSet()
        }
        assertEquals("open_work=0", result.terminationReason)
        assertEquals(3, result.wavesExecuted)
        assertEquals(listOf("a"), seen[0])
        assertTrue(result.snapshot.canComplete)
    }

    private fun node(id: String, dependencies: List<String> = emptyList()): DagNode = DagNode(
        id = id,
        label = id,
        dependencies = dependencies,
        action = DagNodeAction.VERIFY,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        metaFile = PathPlaceholder
    )

    companion object {
        private val PathPlaceholder = java.nio.file.Paths.get("placeholder")
    }
}
