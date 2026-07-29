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

data class HierarchyDispatchContract(
    val taskId: String = "task-${UUID.randomUUID().toString().take(12)}",
    val parentAuthorityId: String,
    val assigneeId: String,
    val sourceCoordinates: List<String>,
    val territory: List<String>,
    val capabilities: List<String>,
    val budgetTokens: Int,
    val acceptanceCriteria: List<String>,
    val rollbackPlan: String,
    val createdAt: Instant = Instant.now()
) {
    fun missingRequiredFields(): List<String> = buildList {
        if (parentAuthorityId.isBlank()) add("parentAuthorityId")
        if (assigneeId.isBlank()) add("assigneeId")
        if (sourceCoordinates.isEmpty()) add("sourceCoordinates")
        if (territory.isEmpty()) add("territory")
        if (capabilities.isEmpty()) add("capabilities")
        if (budgetTokens <= 0) add("budgetTokens")
        if (acceptanceCriteria.isEmpty()) add("acceptanceCriteria")
        if (rollbackPlan.isBlank()) add("rollbackPlan")
    }
}

sealed class HierarchyDispatchResult {
    data class Accepted(val contract: HierarchyDispatchContract, val assignee: AgentRecord) : HierarchyDispatchResult()
    data class Refused(val reason: String) : HierarchyDispatchResult()
}

class HierarchyRegistry {
    private val agents = mutableListOf<AgentRecord>()
    private val dispatches = mutableListOf<HierarchyDispatchContract>()

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

    fun dispatch(contract: HierarchyDispatchContract): HierarchyDispatchResult {
        val missing = contract.missingRequiredFields()
        if (missing.isNotEmpty()) {
            return HierarchyDispatchResult.Refused("dispatch contract missing: ${missing.joinToString(", ")}")
        }
        val parent = get(contract.parentAuthorityId)
            ?: return HierarchyDispatchResult.Refused("parent authority not found: ${contract.parentAuthorityId}")
        val assignee = get(contract.assigneeId)
            ?: return HierarchyDispatchResult.Refused("assignee not found: ${contract.assigneeId}")
        if (!parent.canDispatchTo(assignee)) {
            return HierarchyDispatchResult.Refused("${parent.role} cannot dispatch to ${assignee.role}")
        }
        val uncoveredCapabilities = contract.capabilities.filterNot { it in assignee.capabilities }
        if (uncoveredCapabilities.isNotEmpty()) {
            return HierarchyDispatchResult.Refused("assignee lacks capabilities: ${uncoveredCapabilities.joinToString(", ")}")
        }
        dispatches += contract
        updateStatus(assignee.id, AgentStatus.ASSIGNED, taskId = contract.taskId)
        return HierarchyDispatchResult.Accepted(contract, get(assignee.id) ?: assignee)
    }

    fun dispatchHistory(): List<HierarchyDispatchContract> = dispatches.toList()

    fun escalationPath(agentId: String): List<String> {
        val path = mutableListOf<String>()
        var current = get(agentId)
        while (current != null) {
            path += current.id
            current = current.parentManagerId?.let { get(it) }
        }
        return path
    }

    private fun AgentRecord.canDispatchTo(target: AgentRecord): Boolean = when (role) {
        HierarchyRole.DIRECTOR -> target.role == HierarchyRole.MANAGER || target.role == HierarchyRole.AUDITOR || target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.MANAGER -> target.role == HierarchyRole.SPECIALIST || target.role == HierarchyRole.WORKER
        HierarchyRole.SPECIALIST -> target.role == HierarchyRole.WORKER
        HierarchyRole.WORKER,
        HierarchyRole.AUDITOR,
        HierarchyRole.CUSTODIAN -> false
    }
}
