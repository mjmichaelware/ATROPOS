package atropos.core.planning

/** Single claim boundary for planning graph nodes; lease state stays in the graph owner. */
class GraphClaimService(
    private val graph: PlanningGraphPlugin
) {
    fun claim(nodeId: String, executorId: String, territory: Territory): NodeClaim =
        graph.claimNode(nodeId, executorId, territory)
}
