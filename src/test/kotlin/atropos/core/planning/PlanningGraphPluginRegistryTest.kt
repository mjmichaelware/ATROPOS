package atropos.core.planning

import atropos.core.dag.DagNodeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanningGraphPluginRegistryTest {
    @Test
    fun usesInternalFallbackWhenNoExternalPluginsAreRegistered() {
        val registry = PlanningGraphPluginRegistry()

        val report = registry.report()

        assertEquals("internal-dag", report.selectedId)
        assertTrue(report.fallbackUsed)
        assertTrue(registry.resolve().local)
    }

    @Test
    fun selectsHighestPriorityRegisteredPluginAndAllowsPreferredOverride() {
        val low = PlanningGraphPluginRegistration("low", FakePlanningGraphPlugin(), priority = 1)
        val high = PlanningGraphPluginRegistration("high", FakePlanningGraphPlugin(), priority = 10)
        val registry = PlanningGraphPluginRegistry(listOf(low, high))

        assertEquals("high", registry.resolve().id)
        assertEquals("low", registry.resolve(preferredId = "low").id)
        assertFalse(registry.report().fallbackUsed)
    }

    @Test
    fun rejectsDuplicatePluginIds() {
        assertFailsWith<IllegalArgumentException> {
            PlanningGraphPluginRegistry(
                listOf(
                    PlanningGraphPluginRegistration("dup", FakePlanningGraphPlugin()),
                    PlanningGraphPluginRegistration("dup", FakePlanningGraphPlugin())
                )
            )
        }
    }

    private class FakePlanningGraphPlugin : PlanningGraphPlugin {
        override fun getReadyNodes(projectId: String, graphVersion: String): List<ReadyNode> = emptyList()

        override fun claimNode(nodeId: String, executorId: String, territory: Territory): NodeClaim =
            NodeClaim(true, nodeId, executorId, territory)

        override fun submitEvidence(nodeId: String, evidence: ExecutionEvidence): EvidenceReceipt =
            EvidenceReceipt(nodeId, true)

        override fun completeNode(nodeId: String, result: NodeResult) {
            require(result.finalState != DagNodeState.PENDING) { "completion must move node out of pending" }
        }
    }
}
