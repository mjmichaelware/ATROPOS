package atropos.core.planning

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphTransitionServiceTest {
    @Test
    fun claimed_node_can_reach_terminal_state_and_terminal_node_cannot_move_again() {
        val root = Files.createTempDirectory("atropos-graph-transition-")
        val store = DagStore(root)
        val definition = store.createDag(
            "transition",
            listOf(
                DagNode(
                    id = "node-1",
                    label = "one",
                    action = DagNodeAction.VERIFY,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    metaFile = root.resolve("unused")
                )
            )
        )
        store.claimNode(definition.nodes.single().id, "worker")
        val service = GraphTransitionService(store)

        val completed = service.transition(
            "node-1",
            NodeResult("node-1", true, "complete", DagNodeState.COMPLETE)
        )
        val illegal = service.transition(
            "node-1",
            NodeResult("node-1", false, "late failure", DagNodeState.FAILED)
        )

        assertTrue(completed.accepted, completed.reason)
        assertFalse(illegal.accepted)
        assertTrue(illegal.reason.contains("illegal"))
    }

    @Test
    fun non_terminal_targets_are_not_transition_results() {
        assertFalse(GraphTransitionService.canTransition(DagNodeState.RUNNING, DagNodeState.READY))
        assertTrue(GraphTransitionService.canTransition(DagNodeState.CLAIMED, DagNodeState.FAILED))
        assertFalse(GraphTransitionService.canTransition(DagNodeState.COMPLETE, DagNodeState.FAILED))
    }
}
