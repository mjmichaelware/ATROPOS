package atropos.core.planning

interface PlanningGraphPlugin {
    fun getReadyNodes(projectId: String, graphVersion: String): List<ReadyNode>
    fun claimNode(nodeId: String, executorId: String, territory: Territory): NodeClaim
    fun submitEvidence(nodeId: String, evidence: ExecutionEvidence): EvidenceReceipt
    fun completeNode(nodeId: String, result: NodeResult)
}
