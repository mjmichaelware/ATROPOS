package atropos.core.planning

import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore

data class GraphTransitionDecision(
    val accepted: Boolean,
    val nodeId: String,
    val state: DagNodeState?,
    val reason: String
)

/** Owns typed DAG state transitions while leaving node persistence to DagStore. */
class GraphTransitionService(
    private val store: DagStore
) {
    fun transition(nodeId: String, result: NodeResult): GraphTransitionDecision {
        val node = store.readNode(nodeId)
            ?: return GraphTransitionDecision(false, nodeId, null, "node not found")
        if (!result.finalState.terminal) {
            return GraphTransitionDecision(false, nodeId, node.state, "final transition must target a terminal state")
        }
        if (!canTransition(node.state, result.finalState)) {
            return GraphTransitionDecision(
                false,
                nodeId,
                node.state,
                "illegal graph transition ${node.state} -> ${result.finalState}"
            )
        }
        val updated = store.writeNode(
            node.copy(
                state = result.finalState,
                result = result.result,
                failureReason = result.failureReason,
                lastMessage = result.message,
                claimToken = null,
                claimOwner = null,
                claimExpiresAt = null,
                finishedAt = result.finishedAt
            )
        )
        return GraphTransitionDecision(true, nodeId, updated.state, "transition accepted")
    }

    companion object {
        fun canTransition(from: DagNodeState, to: DagNodeState): Boolean = when (from) {
            DagNodeState.CLAIMED,
            DagNodeState.RUNNING,
            DagNodeState.VERIFYING -> to in setOf(
                DagNodeState.COMPLETE,
                DagNodeState.FAILED,
                DagNodeState.BLOCKED,
                DagNodeState.CANCELLED
            )
            DagNodeState.PENDING,
            DagNodeState.READY -> to == DagNodeState.CANCELLED
            else -> false
        }
    }
}
