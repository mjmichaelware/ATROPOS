package atropos.core.hierarchy

import java.time.Instant
import java.util.UUID

enum class HierarchyRole {
    HUMAN_OWNER,
    DIRECTOR,
    DIVISION_VP,
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
    val dispatches: List<HierarchyDispatchContract> = emptyList(),
    val tasks: List<HierarchyTaskRecord> = emptyList(),
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
    val timeoutAt: Instant? = null,
    val createdAt: Instant = Instant.now()
) {
    fun missingRequiredFields(): List<String> = buildList {
        if (taskId.isBlank()) add("taskId")
        if (parentAuthorityId.isBlank()) add("parentAuthorityId")
        if (assigneeId.isBlank()) add("assigneeId")
        if (sourceCoordinates.isEmpty() || sourceCoordinates.any(String::isBlank)) add("sourceCoordinates")
        if (territory.isEmpty() || territory.any(String::isBlank)) add("territory")
        if (capabilities.isEmpty() || capabilities.any(String::isBlank)) add("capabilities")
        if (budgetTokens <= 0) add("budgetTokens")
        if (acceptanceCriteria.isEmpty() || acceptanceCriteria.any(String::isBlank)) add("acceptanceCriteria")
        if (rollbackPlan.isBlank()) add("rollbackPlan")
    }

    private fun AgentRecord.canDispatchTo(target: AgentRecord): Boolean = when (role) {
        HierarchyRole.HUMAN_OWNER -> target.role == HierarchyRole.DIRECTOR ||
            target.role == HierarchyRole.MANAGER ||
            target.role == HierarchyRole.AUDITOR ||
            target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.DIRECTOR -> target.role == HierarchyRole.MANAGER || target.role == HierarchyRole.AUDITOR || target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.MANAGER -> target.role == HierarchyRole.SPECIALIST || target.role == HierarchyRole.WORKER
        HierarchyRole.SPECIALIST -> target.role == HierarchyRole.WORKER
        HierarchyRole.WORKER,
        HierarchyRole.AUDITOR,
        HierarchyRole.CUSTODIAN -> false
    }

    private fun AgentRecord.territoryRefusal(childTerritory: List<String>): String? {
        val parentTerritory = territoryId
            ?.split(",")
            ?.map { it.trim().trimEnd('/') }
            ?.filter { it.isNotBlank() }
            ?: return null
        if (parentTerritory.isEmpty() || parentTerritory.any { it == "*" || it == "root" }) return null
        val outside = childTerritory
            .map { it.trim().trimEnd('/') }
            .firstOrNull { child ->
                parentTerritory.none { parent -> child == parent || child.startsWith("$parent/") }
            }
        return outside?.let { "dispatch territory outside parent scope: $it not within ${parentTerritory.joinToString(", ")}" }
    }
}

sealed class HierarchyDispatchResult {
    data class Accepted(val contract: HierarchyDispatchContract, val assignee: AgentRecord) : HierarchyDispatchResult()
    data class Refused(val reason: String) : HierarchyDispatchResult()
}
