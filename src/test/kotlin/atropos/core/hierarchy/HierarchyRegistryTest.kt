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
}
