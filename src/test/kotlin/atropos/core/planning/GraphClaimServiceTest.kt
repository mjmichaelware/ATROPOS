package atropos.core.planning

import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphClaimServiceTest {
    @Test
    fun claim_delegates_to_the_single_planning_graph_owner() {
        var received: String? = null
        val graph = object : PlanningGraphPlugin {
            override fun getReadyNodes(projectId: String, graphVersion: String) = emptyList<ReadyNode>()

            override fun claimNode(nodeId: String, executorId: String, territory: Territory): NodeClaim {
                received = "$nodeId:$executorId"
                return NodeClaim(true, nodeId, executorId, territory, claimToken = "token")
            }

            override fun submitEvidence(nodeId: String, evidence: ExecutionEvidence) = EvidenceReceipt(nodeId, true)

            override fun completeNode(nodeId: String, result: NodeResult) = Unit
        }

        val claim = GraphClaimService(graph).claim("node-1", "worker-1", Territory(readPaths = listOf("src")))

        assertTrue(claim.accepted)
        assertEquals("node-1:worker-1", received)
        assertEquals("token", claim.claimToken)
    }
}
