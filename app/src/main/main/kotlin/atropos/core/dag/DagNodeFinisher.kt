package atropos.core.dag

import atropos.core.planning.ExecutionEvidence
import atropos.core.planning.NodeResult
import atropos.core.planning.PlanningGraphPlugin

class DagNodeFinisher(
    private val planningGraph: PlanningGraphPlugin
) {
    fun complete(node: DagNode, result: NodeResult, relatedPaths: List<String> = emptyList()) {
        planningGraph.submitEvidence(
            node.id,
            ExecutionEvidence(
                nodeId = node.id,
                kind = if (result.success) "completion" else "failure",
                detail = result.message,
                relatedPaths = relatedPaths
            )
        )
        planningGraph.completeNode(node.id, result)
    }

    fun fail(node: DagNode, original: DagNode, message: String): DagNodeExecutionResult {
        complete(
            node,
            NodeResult(
                nodeId = original.id,
                success = false,
                message = message,
                finalState = DagNodeState.FAILED,
                failureReason = message
            )
        )
        return DagNodeExecutionResult(original.id, DagNodeState.FAILED, false, message)
    }
}
