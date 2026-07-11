package atropos.core.agent

import java.nio.file.Path
import java.time.Instant

enum class AgentQueueState {
    QUEUED,
    LEASED,
    RUNNING,
    RETRY_WAIT,
    COMPLETED,
    FAILED,
    REFUSED,
    CANCELLED,
    CORRUPT;

    val terminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, REFUSED, CANCELLED, CORRUPT)
}

enum class AgentQueueCheckpoint {
    QUEUED,
    CLAIMED,
    PREFLIGHT_PASSED,
    PLANNED,
    PATCH_GENERATED,
    PATCH_APPLIED,
    VERIFIED,
    REPAIR_GENERATED,
    REPAIR_APPLIED,
    REVERIFIED,
    SMOKE_PASSED,
    SMOKE_FAILED,
    FINALIZED
}

data class AgentQueueLease(
    val token: String,
    val owner: String,
    val acquiredAt: Instant,
    val heartbeatAt: Instant,
    val expiresAt: Instant
) {
    fun isLive(now: Instant): Boolean = expiresAt.isAfter(now)

    fun fingerprint(): String = token.take(10).ifBlank { "none" }
}

data class AgentQueueRecord(
    val id: String,
    val task: String,
    val smokeCommand: String? = null,
    val state: AgentQueueState = AgentQueueState.QUEUED,
    val checkpoint: AgentQueueCheckpoint = AgentQueueCheckpoint.QUEUED,
    val attempts: Int = 0,
    val maxAttempts: Int = AgentQueueDefaults.MAX_ATTEMPTS,
    val jobId: String? = null,
    val provider: String? = null,
    val patchId: String? = null,
    val appliedPatchId: String? = null,
    val verificationId: String? = null,
    val repairId: String? = null,
    val contextExportPath: String? = null,
    val finalJobResult: String? = null,
    val failureReason: String? = null,
    val nextEligibleAt: Instant? = null,
    val lease: AgentQueueLease? = null,
    val cancellationRequested: Boolean = false,
    val cancellationReason: String? = null,
    val cancelledAt: Instant? = null,
    val recoveryCount: Int = 0,
    val lastAttemptAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant? = null,
    val corruptReason: String? = null,
    val metaFile: Path
) {
    fun nextCommand(): String = when {
        state == AgentQueueState.QUEUED || state == AgentQueueState.RETRY_WAIT -> "/agent queue run next"
        state == AgentQueueState.LEASED || state == AgentQueueState.RUNNING -> "/agent queue show $id"
        state == AgentQueueState.COMPLETED -> "/agent job ${jobId ?: "latest"}"
        state == AgentQueueState.REFUSED -> "/agent queue show $id --raw"
        state == AgentQueueState.CANCELLED -> "/agent queue show $id --raw"
        state == AgentQueueState.FAILED && attempts < maxAttempts -> "/agent queue resume $id"
        state == AgentQueueState.FAILED -> "/agent queue show $id --raw"
        state == AgentQueueState.CORRUPT -> "inspect ${metaFile}"
        else -> "/agent queue show $id"
    }

    fun renderRaw(): String = buildString {
        appendLine("queue id: $id")
        appendLine("task: $task")
        appendLine("smoke command: ${smokeCommand ?: "none"}")
        appendLine("state: $state")
        appendLine("checkpoint: $checkpoint")
        appendLine("attempts: $attempts")
        appendLine("maximum attempts: $maxAttempts")
        appendLine("job id: ${jobId ?: "none"}")
        appendLine("provider: ${provider ?: "none"}")
        appendLine("patch id: ${patchId ?: "none"}")
        appendLine("applied patch id: ${appliedPatchId ?: "none"}")
        appendLine("verification id: ${verificationId ?: "none"}")
        appendLine("repair id: ${repairId ?: "none"}")
        appendLine("context export path: ${contextExportPath ?: "none"}")
        appendLine("final job result: ${finalJobResult ?: "none"}")
        appendLine("failure reason: ${failureReason ?: "none"}")
        appendLine("next eligible at: ${nextEligibleAt ?: "none"}")
        appendLine("lease owner: ${lease?.owner ?: "none"}")
        appendLine("lease token fingerprint: ${lease?.fingerprint() ?: "none"}")
        appendLine("lease acquired at: ${lease?.acquiredAt ?: "none"}")
        appendLine("lease heartbeat at: ${lease?.heartbeatAt ?: "none"}")
        appendLine("lease expires at: ${lease?.expiresAt ?: "none"}")
        appendLine("cancellation requested: $cancellationRequested")
        appendLine("cancellation reason: ${cancellationReason ?: "none"}")
        appendLine("cancelled at: ${cancelledAt ?: "none"}")
        appendLine("recovery count: $recoveryCount")
        appendLine("last attempt at: ${lastAttemptAt ?: "none"}")
        appendLine("created at: $createdAt")
        appendLine("updated at: $updatedAt")
        appendLine("finished at: ${finishedAt ?: "none"}")
        appendLine("corrupt reason: ${corruptReason ?: "none"}")
        appendLine("next recommended command: ${nextCommand()}")
        appendLine("record file: $metaFile")
    }.trimEnd()
}

object AgentQueueDefaults {
    const val MAX_ATTEMPTS: Int = 2
    const val MIN_LEASE_SECONDS: Long = 60L
    const val DEFAULT_LEASE_SECONDS: Long = 15L * 60L
    const val MAX_LEASE_SECONDS: Long = 60L * 60L
    const val MAX_RUN_COUNT: Int = 10
}

object AgentQueueTransitions {
    private val allowed = mapOf(
        AgentQueueState.QUEUED to setOf(AgentQueueState.LEASED, AgentQueueState.CANCELLED, AgentQueueState.REFUSED, AgentQueueState.CORRUPT),
        AgentQueueState.LEASED to setOf(AgentQueueState.RUNNING, AgentQueueState.QUEUED, AgentQueueState.FAILED, AgentQueueState.CANCELLED),
        AgentQueueState.RUNNING to setOf(
            AgentQueueState.COMPLETED,
            AgentQueueState.FAILED,
            AgentQueueState.REFUSED,
            AgentQueueState.CANCELLED,
            AgentQueueState.RETRY_WAIT
        ),
        AgentQueueState.RETRY_WAIT to setOf(AgentQueueState.LEASED, AgentQueueState.CANCELLED, AgentQueueState.FAILED),
        AgentQueueState.COMPLETED to emptySet(),
        AgentQueueState.FAILED to emptySet(),
        AgentQueueState.REFUSED to emptySet(),
        AgentQueueState.CANCELLED to emptySet(),
        AgentQueueState.CORRUPT to emptySet()
    )

    fun canTransition(from: AgentQueueState, to: AgentQueueState): Boolean =
        from == to || allowed[from].orEmpty().contains(to)

    fun requireTransition(from: AgentQueueState, to: AgentQueueState) {
        require(canTransition(from, to)) { "invalid queue transition: $from -> $to" }
    }

    fun isSelectable(record: AgentQueueRecord, now: Instant): Boolean {
        if (record.state !in setOf(AgentQueueState.QUEUED, AgentQueueState.RETRY_WAIT)) return false
        val eligibleAt = record.nextEligibleAt
        return eligibleAt == null || !eligibleAt.isAfter(now)
    }

    fun isRecoverableLease(record: AgentQueueRecord, now: Instant): Boolean =
        record.state in setOf(AgentQueueState.LEASED, AgentQueueState.RUNNING) &&
            record.lease != null &&
            !record.lease.isLive(now)
}
