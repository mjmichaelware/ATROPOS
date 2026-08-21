package atropos.core.factory

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactoryObligationLoopTest {
    @Test
    fun verified_factory_wave_closes_only_the_existing_dag() {
        val root = Files.createTempDirectory("factory-loop")
        val store = DagStore(root)
        val dag = store.createDag(
            label = "calculator",
            nodes = listOf(
                node("requirements"),
                node("source", dependencies = listOf("requirements"))
            ),
            projectId = "run-1"
        )
        val loop = FactoryObligationLoop(store)
        val before = loop.beforeMutation(dag)
        assertEquals(listOf("requirements"), before.runnableAtomIds)

        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = "a".repeat(64),
            researchSha256 = "b".repeat(64),
            atomIds = dag.nodes.map { it.id },
            promptSpans = "CLI@1-1|class=surface"
        )
        val after = loop.finalizeAfterVerifiedEvidence(dag.id, freeze)
        assertTrue(after.canComplete)
        assertEquals(2, after.doneAtomIds.size)
        assertTrue(store.readDag(dag.id)!!.nodes.all { it.state == DagNodeState.COMPLETE })
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
