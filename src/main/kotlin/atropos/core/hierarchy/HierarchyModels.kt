package atropos.core.hierarchy

import java.time.Instant
import java.util.UUID

enum class HierarchyRole {
    DIRECTOR,
    MANAGER,
    SPECIALIST,
    WORKER,
    AUDITOR,
    CUSTODIAN
}

enum class AgentStatus { IDLE, ASSIGNED, WORKING, BLOCKED, COMPLETED, FAILED }

data class AgentRecord(
    val id: String = "agent-${UUID.randomUUID().toString().take(12)}",
    val name: String,
    val role: HierarchyRole,
    val territoryId: String? = null,
    val parentManagerId: String? = null,
    val status: AgentStatus = AgentStatus.IDLE,
    val capabilities: List<String> = emptyList(),
    val currentTaskId: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastHeartbeat: Instant = Instant.now(),
    val taskHistory: List<String> = emptyList()
)

data class HierarchySnapshot(
    val agents: List<AgentRecord>,
    val timestamp: Instant = Instant.now()
) {
    fun byRole(role: HierarchyRole): List<AgentRecord> = agents.filter { it.role == role }
    fun byManager(managerId: String): List<AgentRecord> = agents.filter { it.parentManagerId == managerId }
    fun idle(): List<AgentRecord> = agents.filter { it.status == AgentStatus.IDLE }
}

class HierarchyRegistry {
    private val agents = mutableListOf<AgentRecord>()

    fun register(agent: AgentRecord) {
        val idx = agents.indexOfFirst { it.id == agent.id }
        if (idx >= 0) agents[idx] = agent else agents += agent
    }

    fun get(id: String): AgentRecord? = agents.firstOrNull { it.id == id }
    fun getAll(): List<AgentRecord> = agents.toList()
    fun byRole(role: HierarchyRole): List<AgentRecord> = agents.filter { it.role == role }

    fun updateStatus(id: String, status: AgentStatus, taskId: String? = null) {
        val idx = agents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            agents[idx] = agents[idx].copy(status = status, currentTaskId = taskId, lastHeartbeat = Instant.now())
        }
    }

    fun assignTerritory(id: String, territoryId: String) {
        val idx = agents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            agents[idx] = agents[idx].copy(territoryId = territoryId)
        }
    }

    fun assignManager(id: String, managerId: String) {
        val idx = agents.indexOfFirst { it.id == id }
        if (idx >= 0) {
            agents[idx] = agents[idx].copy(parentManagerId = managerId)
        }
    }

    fun snapshot(): HierarchySnapshot = HierarchySnapshot(agents = agents.toList())

    fun escalationPath(agentId: String): List<String> {
        val path = mutableListOf<String>()
        var current = get(agentId)
        while (current != null) {
            path += current.id
            current = current.parentManagerId?.let { get(it) }
        }
        return path
    }
}
