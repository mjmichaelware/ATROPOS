package atropos.core.hierarchy

import java.time.Instant

/**
 * Canonical durable-in-process owner for hierarchy membership and dispatch.
 * Contract validation stays on [HierarchyDispatchContract]; this registry owns
 * assignment state, role transitions, and escalation history only.
 */
class HierarchyRegistry {
    private val agents = mutableListOf<AgentRecord>()
    private val dispatches = mutableListOf<HierarchyDispatchContract>()
    private val tasks = mutableListOf<HierarchyTaskRecord>()

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
        if (idx >= 0) agents[idx] = agents[idx].copy(territoryId = territoryId)
    }

    fun assignManager(id: String, managerId: String) {
        val idx = agents.indexOfFirst { it.id == id }
        if (idx >= 0) agents[idx] = agents[idx].copy(parentManagerId = managerId)
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
        if (dispatches.any { it.taskId == contract.taskId }) {
            return HierarchyDispatchResult.Refused("task already dispatched: ${contract.taskId}")
        }
        if (parent.status == AgentStatus.BLOCKED || parent.status == AgentStatus.FAILED) {
            return HierarchyDispatchResult.Refused("parent authority is not dispatchable: ${parent.status}")
        }
        if (assignee.status != AgentStatus.IDLE) {
            return HierarchyDispatchResult.Refused("assignee is not idle: ${assignee.status}")
        }
        if (!parent.canDispatchTo(assignee)) {
            return HierarchyDispatchResult.Refused("${parent.role} cannot dispatch to ${assignee.role}")
        }
        val uncoveredCapabilities = contract.capabilities.filterNot { it in assignee.capabilities }
        if (uncoveredCapabilities.isNotEmpty()) {
            return HierarchyDispatchResult.Refused("assignee lacks capabilities: ${uncoveredCapabilities.joinToString(", ")}")
        }
        val territoryRefusal = parent.territoryRefusal(contract.territory)
        if (territoryRefusal != null) return HierarchyDispatchResult.Refused(territoryRefusal)

        dispatches += contract
        tasks += HierarchyTaskRecord(contract = contract)
        assignTerritory(assignee.id, contract.territory.joinToString(","))
        updateStatus(assignee.id, AgentStatus.ASSIGNED, taskId = contract.taskId)
        return HierarchyDispatchResult.Accepted(contract, get(assignee.id) ?: assignee)
    }

    fun dispatchHistory(): List<HierarchyDispatchContract> = dispatches.toList()

    fun task(taskId: String): HierarchyTaskRecord? = tasks.firstOrNull { it.contract.taskId == taskId }

    fun taskHistory(): List<HierarchyTaskRecord> = tasks.toList()

    fun startTask(taskId: String): Boolean = updateTask(taskId) { task ->
        if (task.state != HierarchyTaskState.DISPATCHED) return@updateTask null
        updateStatus(task.contract.assigneeId, AgentStatus.WORKING, taskId)
        task.copy(state = HierarchyTaskState.RUNNING, updatedAt = Instant.now())
    }

    fun completeTask(taskId: String, result: String): Boolean = updateTask(taskId) { task ->
        if (task.state !in setOf(HierarchyTaskState.DISPATCHED, HierarchyTaskState.RUNNING)) return@updateTask null
        if (result.isBlank()) return@updateTask null
        updateStatus(task.contract.assigneeId, AgentStatus.COMPLETED, taskId)
        task.copy(state = HierarchyTaskState.COMPLETED, result = result.trim().take(4_000), updatedAt = Instant.now())
    }

    fun failTask(taskId: String, reason: String): Boolean = updateTask(taskId) { task ->
        if (task.state in setOf(HierarchyTaskState.COMPLETED, HierarchyTaskState.FAILED, HierarchyTaskState.EXPIRED)) return@updateTask null
        if (reason.isBlank()) return@updateTask null
        updateStatus(task.contract.assigneeId, AgentStatus.FAILED, taskId)
        task.copy(state = HierarchyTaskState.FAILED, result = reason.trim().take(4_000), updatedAt = Instant.now())
    }

    fun expireTasks(now: Instant = Instant.now()): List<String> {
        val expired = tasks.filter { task ->
            task.state in setOf(HierarchyTaskState.DISPATCHED, HierarchyTaskState.RUNNING) &&
                task.contract.timeoutAt?.isAfter(now) == false
        }.map { it.contract.taskId }
        expired.forEach { taskId ->
            updateTask(taskId) { task ->
                updateStatus(task.contract.assigneeId, AgentStatus.BLOCKED, taskId)
                task.copy(state = HierarchyTaskState.EXPIRED, result = "task timeout expired", updatedAt = now)
            }
        }
        return expired
    }

    fun aggregateResults(parentAuthorityId: String): HierarchyResultAggregation {
        val children = tasks.filter { it.contract.parentAuthorityId == parentAuthorityId }
        return HierarchyResultAggregation(
            parentAuthorityId = parentAuthorityId,
            total = children.size,
            completed = children.count { it.state == HierarchyTaskState.COMPLETED },
            failed = children.count { it.state == HierarchyTaskState.FAILED },
            blocked = children.count { it.state == HierarchyTaskState.BLOCKED },
            expired = children.count { it.state == HierarchyTaskState.EXPIRED },
            pending = children.count { it.state == HierarchyTaskState.DISPATCHED || it.state == HierarchyTaskState.RUNNING },
            results = children.mapNotNull { task -> task.result?.takeIf { it.isNotBlank() } }
        )
    }

    fun escalationPath(agentId: String): List<String> {
        val path = mutableListOf<String>()
        var current = get(agentId)
        while (current != null) {
            path += current.id
            current = current.parentManagerId?.let { get(it) }
        }
        return path
    }

    private fun updateTask(taskId: String, transform: (HierarchyTaskRecord) -> HierarchyTaskRecord?): Boolean {
        val index = tasks.indexOfFirst { it.contract.taskId == taskId }
        if (index < 0) return false
        val updated = transform(tasks[index]) ?: return false
        tasks[index] = updated
        return true
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
        if (role == HierarchyRole.HUMAN_OWNER &&
            (parentTerritory == null || parentTerritory.any { it == "*" || it == "root" })
        ) return null
        if (parentTerritory.isNullOrEmpty()) {
            return "parent authority has no bounded territory: $id"
        }
        val outside = childTerritory
            .map { it.trim().trimEnd('/') }
            .firstOrNull { child ->
                parentTerritory.none { parent -> child == parent || child.startsWith("$parent/") }
            }
        return outside?.let { "dispatch territory outside parent scope: $it not within ${parentTerritory.joinToString(", ")}" }
    }
}
