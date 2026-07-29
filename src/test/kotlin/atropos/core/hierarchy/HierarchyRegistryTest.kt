package atropos.core.hierarchy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HierarchyRegistryTest {
    @Test
    fun registerAndRetrieve() {
        val reg = HierarchyRegistry()
        val agent = AgentRecord(name = "director-1", role = HierarchyRole.DIRECTOR)
        reg.register(agent)
        assertEquals(agent.id, reg.get(agent.id)?.id)
        assertEquals(1, reg.byRole(HierarchyRole.DIRECTOR).size)
    }

    @Test
    fun updateStatusChangesWorkingState() {
        val reg = HierarchyRegistry()
        val agent = AgentRecord(name = "worker-1", role = HierarchyRole.WORKER)
        reg.register(agent)
        reg.updateStatus(agent.id, AgentStatus.WORKING, taskId = "task-42")
        val updated = reg.get(agent.id)!!
        assertEquals(AgentStatus.WORKING, updated.status)
        assertEquals("task-42", updated.currentTaskId)
    }

    @Test
    fun escalationPathWalksUpHierarchy() {
        val reg = HierarchyRegistry()
        val director = AgentRecord(name = "director", role = HierarchyRole.DIRECTOR)
        val manager = AgentRecord(name = "manager", role = HierarchyRole.MANAGER)
        val worker = AgentRecord(name = "worker", role = HierarchyRole.WORKER)

        reg.register(director)
        reg.register(manager)
        reg.register(worker)

        reg.assignManager(worker.id, manager.id)
        reg.assignManager(manager.id, director.id)

        val path = reg.escalationPath(worker.id)
        assertEquals(3, path.size)
        assertEquals(worker.id, path[0])
        assertEquals(manager.id, path[1])
        assertEquals(director.id, path[2])
    }

    @Test
    fun snapshotCapturesAllAgents() {
        val reg = HierarchyRegistry()
        reg.register(AgentRecord(name = "a1", role = HierarchyRole.WORKER))
        reg.register(AgentRecord(name = "a2", role = HierarchyRole.SPECIALIST))
        val snap = reg.snapshot()
        assertEquals(2, snap.agents.size)
    }

    @Test
    fun dispatchCarriesSourceTerritoryBudgetAcceptanceRollbackAndParentAuthority() {
        val reg = HierarchyRegistry()
        val manager = AgentRecord(name = "manager", role = HierarchyRole.MANAGER)
        val worker = AgentRecord(
            name = "worker",
            role = HierarchyRole.WORKER,
            capabilities = listOf("kotlin", "verification")
        )
        reg.register(manager)
        reg.register(worker)

        val contract = HierarchyDispatchContract(
            parentAuthorityId = manager.id,
            assigneeId = worker.id,
            sourceCoordinates = listOf("97cff09c0f362337:S0013@L46-L48"),
            territory = listOf("src/main/kotlin/atropos/core/agent"),
            capabilities = listOf("kotlin"),
            budgetTokens = 1200,
            acceptanceCriteria = listOf("focused test exists", "diff scoped"),
            rollbackPlan = "revert exact files in this dispatch"
        )
        val result = reg.dispatch(contract)

        assertTrue(result is HierarchyDispatchResult.Accepted)
        assertEquals(AgentStatus.ASSIGNED, reg.get(worker.id)?.status)
        assertEquals(contract.taskId, reg.get(worker.id)?.currentTaskId)
        assertEquals(listOf(contract), reg.dispatchHistory())
    }

    @Test
    fun dispatchRefusesMissingContractFieldsAndInvalidAuthorityDirection() {
        val reg = HierarchyRegistry()
        val workerA = AgentRecord(name = "worker-a", role = HierarchyRole.WORKER, capabilities = listOf("kotlin"))
        val workerB = AgentRecord(name = "worker-b", role = HierarchyRole.WORKER, capabilities = listOf("kotlin"))
        reg.register(workerA)
        reg.register(workerB)

        val missing = reg.dispatch(
            HierarchyDispatchContract(
                parentAuthorityId = workerA.id,
                assigneeId = workerB.id,
                sourceCoordinates = emptyList(),
                territory = listOf("src"),
                capabilities = listOf("kotlin"),
                budgetTokens = 100,
                acceptanceCriteria = listOf("done"),
                rollbackPlan = "none"
            )
        )
        val invalidDirection = reg.dispatch(
            HierarchyDispatchContract(
                parentAuthorityId = workerA.id,
                assigneeId = workerB.id,
                sourceCoordinates = listOf("source:S1@L1-L2"),
                territory = listOf("src"),
                capabilities = listOf("kotlin"),
                budgetTokens = 100,
                acceptanceCriteria = listOf("done"),
                rollbackPlan = "revert exact files"
            )
        )

        assertTrue(missing is HierarchyDispatchResult.Refused)
        assertTrue((missing as HierarchyDispatchResult.Refused).reason.contains("sourceCoordinates"))
        assertTrue(invalidDirection is HierarchyDispatchResult.Refused)
        assertTrue((invalidDirection as HierarchyDispatchResult.Refused).reason.contains("cannot dispatch"))
        assertEquals(AgentStatus.IDLE, reg.get(workerB.id)?.status)
    }
}
