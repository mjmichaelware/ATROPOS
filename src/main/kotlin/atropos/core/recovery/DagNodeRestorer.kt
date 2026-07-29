package atropos.core.recovery

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore

class DagNodeRestorer(
    private val dagStore: DagStore = DagStore()
) {
    fun restoreInterruptedNodes(dagId: String? = null): List<DagNodeRestoreResult> {
        val dags = dagStore.listDags().filter { dagId == null || it.id == dagId }
        val results = mutableListOf<DagNodeRestoreResult>()
        for (dag in dags) {
            for (node in dag.nodes) {
                if (node.state == DagNodeState.CLAIMED || node.state == DagNodeState.RUNNING || node.state == DagNodeState.VERIFYING) {
                    results += restoreNode(node)
                }
            }
        }
        return results
    }

    private fun restoreNode(node: DagNode): DagNodeRestoreResult {
        if (node.attempts >= node.maxAttempts) {
            dagStore.writeNode(
                node.copy(
                    state = DagNodeState.BLOCKED,
                    claimToken = null,
                    claimOwner = null,
                    claimExpiresAt = null,
                    failureReason = "restore blocked: retry budget exhausted"
                )
            )
            return DagNodeRestoreResult(node.id, false, "retry budget exhausted")
        }

        dagStore.writeNode(
            node.copy(
                state = DagNodeState.READY,
                claimToken = null,
                claimOwner = null,
                claimExpiresAt = null,
                lastMessage = "restored after restart"
            )
        )
        return DagNodeRestoreResult(node.id, true, "restored to READY")
    }
}
