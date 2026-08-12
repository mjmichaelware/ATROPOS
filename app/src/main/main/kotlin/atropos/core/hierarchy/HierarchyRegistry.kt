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

    fun assignTerritory(id: String, territoryId: String): Boolean {
        if (id.isBlank() || isUnsafeTerritory(territoryId)) return false
        val idx = agents.indexOfFirst { it.id == id }
        if (idx < 0) return false
        agents[idx] = agents[idx].copy(territoryId = territoryId)
        return true
    }

    fun assignManager(id: String, managerId: String): Boolean {
        val idx = agents.indexOfFirst { it.id == id }
        val child = agents.getOrNull(idx)
        val manager = agents.firstOrNull { it.id == managerId }
        if (child == null || id == managerId || manager == null) return false
        if (!child.role.acceptsParentRole(manager.role)) return false
        if (wouldCreateManagerCycle(id, managerId)) return false
        agents[idx] = agents[idx].copy(parentManagerId = managerId)
        return true
    }

    fun snapshot(): HierarchySnapshot = HierarchySnapshot(
        agents = agents.toList(),
        dispatches = dispatches.toList(),
        tasks = tasks.toList()
    )

    fun dispatch(contract: HierarchyDispatchContract): HierarchyDispatchResult {
        val missing = contract.missingRequiredFields()
        if (missing.isNotEmpty()) {
            return HierarchyDispatchResult.Refused("dispatch contract missing: ${missing.joinToString(", ")}")
        }
        if (tasks.any { it.contract.taskId == contract.taskId }) {
            return HierarchyDispatchResult.Refused("dispatch task id already exists: ${contract.taskId}")
        }
        val unsafeTerritories = contract.territory.filter(::isUnsafeTerritory)
        if (unsafeTerritories.isNotEmpty()) {
            return HierarchyDispatchResult.Refused(
                "dispatch territory contains unsafe path entries: ${unsafeTerritories.joinToString(", ")}"
            )
        }
        if (contract.timeoutAt?.isAfter(Instant.now()) == false) {
            return HierarchyDispatchResult.Refused("dispatch contract timeout has already elapsed")
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
        // COMPLETED means the agent's previous task finished successfully; it
        // remains eligible for another bounded assignment. Failed and blocked
        // agents require explicit recovery before they can receive work.
        if (assignee.status !in setOf(AgentStatus.IDLE, AgentStatus.COMPLETED)) {
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

        val assignedTerritory = contract.territory.joinToString(",")
        if (!assignTerritory(assignee.id, assignedTerritory)) {
            return HierarchyDispatchResult.Refused("assignee territory assignment was refused")
        }
        dispatches += contract
        tasks += HierarchyTaskRecord(contract = contract)
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
        updateStatus(task.contract.assigneeId, AgentStatus.COMPLETED)
        task.copy(state = HierarchyTaskState.COMPLETED, result = result.trim().take(4_000), updatedAt = Instant.now())
    }

    fun failTask(taskId: String, reason: String): Boolean = updateTask(taskId) { task ->
        if (task.state in setOf(HierarchyTaskState.COMPLETED, HierarchyTaskState.FAILED, HierarchyTaskState.BLOCKED, HierarchyTaskState.EXPIRED)) return@updateTask null
        if (reason.isBlank()) return@updateTask null
        updateStatus(task.contract.assigneeId, AgentStatus.FAILED)
        task.copy(state = HierarchyTaskState.FAILED, result = reason.trim().take(4_000), updatedAt = Instant.now())
    }

    fun blockTask(taskId: String, reason: String): Boolean = updateTask(taskId) { task ->
        if (task.state in setOf(HierarchyTaskState.COMPLETED, HierarchyTaskState.FAILED, HierarchyTaskState.BLOCKED, HierarchyTaskState.EXPIRED)) {
            return@updateTask null
        }
        if (reason.isBlank()) return@updateTask null
        updateStatus(task.contract.assigneeId, AgentStatus.BLOCKED)
        task.copy(state = HierarchyTaskState.BLOCKED, result = reason.trim().take(4_000), updatedAt = Instant.now())
    }

    fun expireTasks(now: Instant = Instant.now()): List<String> {
        val expired = tasks.filter { task ->
            task.state in setOf(HierarchyTaskState.DISPATCHED, HierarchyTaskState.RUNNING) &&
                task.contract.timeoutAt?.isAfter(now) == false
        }.map { it.contract.taskId }
        expired.forEach { taskId ->
            updateTask(taskId) { task ->
                updateStatus(task.contract.assigneeId, AgentStatus.BLOCKED)
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
        val visited = mutableSetOf<String>()
        var current = get(agentId)
        while (current != null && visited.add(current.id)) {
            path += current.id
            current = current.parentManagerId?.let { get(it) }
        }
        return path
    }

    private fun wouldCreateManagerCycle(id: String, managerId: String): Boolean {
        val visited = mutableSetOf<String>()
        var current = get(managerId)
        while (current != null && visited.add(current.id)) {
            if (current.id == id) return true
            current = current.parentManagerId?.let { get(it) }
        }
        return current != null
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
            target.role == HierarchyRole.DIVISION_VP ||
            target.role == HierarchyRole.MANAGER ||
            target.role == HierarchyRole.AUDITOR ||
            target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.DIRECTOR -> target.role == HierarchyRole.MANAGER || target.role == HierarchyRole.AUDITOR || target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.DIVISION_VP -> target.role == HierarchyRole.MANAGER || target.role == HierarchyRole.AUDITOR || target.role == HierarchyRole.CUSTODIAN
        HierarchyRole.MANAGER -> target.role == HierarchyRole.SPECIALIST || target.role == HierarchyRole.WORKER
        HierarchyRole.SPECIALIST -> target.role == HierarchyRole.WORKER
        HierarchyRole.WORKER,
        HierarchyRole.AUDITOR,
        HierarchyRole.CUSTODIAN -> false
    }

    private fun HierarchyRole.acceptsParentRole(parent: HierarchyRole): Boolean = when (this) {
        HierarchyRole.DIRECTOR,
        HierarchyRole.DIVISION_VP -> parent == HierarchyRole.HUMAN_OWNER
        HierarchyRole.MANAGER -> parent == HierarchyRole.DIRECTOR || parent == HierarchyRole.DIVISION_VP
        HierarchyRole.SPECIALIST -> parent == HierarchyRole.MANAGER
        HierarchyRole.WORKER -> parent == HierarchyRole.MANAGER || parent == HierarchyRole.SPECIALIST
        HierarchyRole.HUMAN_OWNER,
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

    private fun isUnsafeTerritory(raw: String): Boolean {
        val normalized = raw.replace('\\', '/')
        return normalized.isBlank() ||
            raw.contains('\\') ||
            raw.indexOf('\u0000') >= 0 ||
            normalized.startsWith('/') ||
            normalized.split('/').any { it.isBlank() || it == "." || it == ".." }
    }
}
