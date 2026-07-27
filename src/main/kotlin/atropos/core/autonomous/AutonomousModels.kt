package atropos.core.autonomous

import java.time.Instant
import java.util.UUID

enum class AutonomousTaskKind {
    DAG_INGESTION, DAG_CONTINUATION, PROVIDER_FAILOVER, REPAIR_RETRY,
    TERRITORY_SYNC, MEMORY_COMPACTION, HIG_REDUCTION, POLICY_APPLICATION,
    AUDIT_RUN, CUSTODIAN_CLEAN, VERIFICATION_GATE, ARTIFACT_BUILD
}

enum class AutonomousTaskPriority { LOW, MEDIUM, HIGH, CRITICAL }

enum class AutonomousTaskState { PENDING, ELIGIBLE, RUNNING, COMPLETED, FAILED, SKIPPED, SUPERSEDED }

data class AutonomousTask(
    val id: String = "auto-${UUID.randomUUID().toString().take(12)}",
    val kind: AutonomousTaskKind,
    val priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM,
    val state: AutonomousTaskState = AutonomousTaskState.PENDING,
    val description: String,
    val context: Map<String, String> = emptyMap(),
    val dependencies: List<String> = emptyList(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val createdAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val result: String? = null
)

data class AutonomousBacklog(
    val tasks: List<AutonomousTask> = emptyList(),
    val timestamp: Instant = Instant.now()
) {
    val pendingCount: Int get() = tasks.count { it.state == AutonomousTaskState.PENDING }
    val eligibleCount: Int get() = tasks.count { it.state == AutonomousTaskState.ELIGIBLE }
    val runningCount: Int get() = tasks.count { it.state == AutonomousTaskState.RUNNING }
    val completedCount: Int get() = tasks.count { it.state == AutonomousTaskState.COMPLETED }
    val failedCount: Int get() = tasks.count { it.state == AutonomousTaskState.FAILED }

    fun eligibleTasks(): List<AutonomousTask> {
        return tasks.filter { task ->
            task.state == AutonomousTaskState.ELIGIBLE &&
            task.dependencies.all { depId ->
                tasks.firstOrNull { it.id == depId }?.state == AutonomousTaskState.COMPLETED
            }
        }
    }

    fun byKind(kind: AutonomousTaskKind): List<AutonomousTask> = tasks.filter { it.kind == kind }

    val highestPriority: AutonomousTaskPriority get() =
        tasks.filter { it.state == AutonomousTaskState.ELIGIBLE }.minOfOrNull { it.priority.ordinal }
            ?.let { AutonomousTaskPriority.entries[it] } ?: AutonomousTaskPriority.LOW

    val summary: String get() = "Backlog: $pendingCount pending, $eligibleCount eligible, $runningCount running, $completedCount completed, $failedCount failed"
}

data class RepairRecord(
    val id: String = "repair-${UUID.randomUUID().toString().take(12)}",
    val taskId: String,
    val failureSignature: String,
    val repairAction: String,
    val success: Boolean,
    val attemptNumber: Int = 1,
    val durationMs: Long = 0,
    val createdAt: Instant = Instant.now()
)

data class RepairMemory(
    val records: List<RepairRecord> = emptyList(),
    val timestamp: Instant = Instant.now()
) {
    fun findBySignature(signature: String): List<RepairRecord> = records.filter { it.failureSignature == signature }
    fun successCount(): Int = records.count { it.success }
    fun failCount(): Int = records.count { !it.success }

    val summary: String get() = "RepairMemory: ${records.size} records, ${successCount()} successes, ${failCount()} failures"
}

data class ProviderFailoverEvent(
    val id: String = "failover-${UUID.randomUUID().toString().take(12)}",
    val primaryProviderId: String,
    val fallbackProviderId: String,
    val failureReason: String,
    val durationMs: Long = 0,
    val success: Boolean = false,
    val escalated: Boolean = false,
    val timestamp: Instant = Instant.now()
)

data class StopCondition(
    val kind: String,
    val description: String,
    val triggered: Boolean = false,
    val threshold: Double = 0.0,
    val currentValue: Double = 0.0,
    val triggerTime: Instant? = null
) {
    val met: Boolean get() = triggered && currentValue >= threshold
}

data class AutonomousSession(
    val id: String = "session-${UUID.randomUUID().toString().take(12)}",
    val startedAt: Instant = Instant.now(),
    val tasksAttempted: Int = 0,
    val tasksCompleted: Int = 0,
    val tasksFailed: Int = 0,
    val repairsApplied: Int = 0,
    val failoverEvents: Int = 0,
    val stopConditions: List<StopCondition> = emptyList(),
    val active: Boolean = true
) {
    val progress: Double get() = if (tasksAttempted > 0) tasksCompleted.toDouble() / tasksAttempted.toDouble() else 0.0

    val summary: String get() {
        val stopped = stopConditions.firstOrNull { it.met }
        return "Session: $tasksAttempted attempted, $tasksCompleted completed, $tasksFailed failed, $repairsApplied repairs, ${failoverEvents}failovers" +
            (if (stopped != null) " | STOP: ${stopped.description}" else "")
    }
}
