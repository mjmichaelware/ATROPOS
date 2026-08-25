package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.provider.ProviderOnboardingService
import java.time.Instant

/**
 * Executes queue entries and manages job execution lifecycle.
 *
 * Handles job execution, state transitions, and checkpoint management.
 */
internal class AgentQueueExecutor(
    private val config: AtroposConfig,
    private val collector: AgentContextCollector,
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val runService: AgentRunService? = null,
    private val store: AgentQueueStore = AgentQueueStore(collector.repoRoot),
    private val clock: () -> Instant = { Instant.now() },
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile())
) {
    fun executeClaimed(activeProviderName: String, claimed: AgentQueueRecord): AgentQueueRunResult {
        var queueRecord = store.markRunning(claimed)
        val leaseToken = queueRecord.lease?.token

        fun sync(checkpoint: AgentQueueCheckpoint, job: AgentJobRecord?, message: String, duration: Long = leaseSeconds()) {
            val heartbeat = store.heartbeat(queueRecord.id, leaseToken, checkpoint, duration) ?: queueRecord
            val synced = copyFromJob(heartbeat, job, checkpoint)
            queueRecord = store.update(
                synced,
                eventType = "checkpoint",
                previousState = synced.state,
                message = message
            )
        }

        fun before(checkpoint: AgentQueueCheckpoint, job: AgentJobRecord?) {
            val latest = store.resolve(queueRecord.id) ?: queueRecord
            queueRecord = latest
            if (latest.cancellationRequested) {
                val cancelledAt = clock()
                queueRecord = store.update(
                    latest.copy(
                        state = AgentQueueState.CANCELLED,
                        lease = null,
                        cancelledAt = cancelledAt,
                        finishedAt = cancelledAt,
                        failureReason = latest.cancellationReason ?: "queue entry cancelled"
                    ),
                    eventType = "cancelled",
                    previousState = latest.state,
                    message = latest.cancellationReason ?: "queue entry cancelled"
                )
                throw AgentRunCancelledException("queue entry cancelled")
            }
            val duration = when (checkpoint) {
                AgentQueueCheckpoint.PATCH_APPLIED,
                AgentQueueCheckpoint.REPAIR_APPLIED,
                AgentQueueCheckpoint.SMOKE_PASSED -> operationLeaseSeconds()
                else -> leaseSeconds()
            }
            sync(checkpoint, job, "lease renewed before $checkpoint", duration)
        }

        val hooks = AgentRunHooks(
            checkpointHandler = { checkpoint, job, message -> sync(checkpoint, job, message) },
            beforeStageHandler = { checkpoint, job -> before(checkpoint, job) }
        )

        return try {
            val job = (runService ?: AgentRunService(config, collector, onboarding))
                .run(activeProviderName, queueRecord.task, queueRecord.smokeCommand, hooks)
            val terminal = finalizeFromJob(queueRecord, job)
            rememberQueue(terminal, "finalized")
            AgentQueueRunResult(terminal, job, "queue entry finalized as ${terminal.state}", ran = true)
        } catch (_: AgentRunCancelledException) {
            AgentQueueRunResult(queueRecord, null, "queue entry cancelled before next stage", ran = true)
        } catch (failure: Exception) {
            val failed = finalizeInterrupted(queueRecord, failure.message ?: failure.javaClass.simpleName)
            rememberQueue(failed, "interrupted")
            AgentQueueRunResult(failed, null, failed.failureReason ?: "queue execution interrupted", ran = true)
        }
    }

    private fun finalizeFromJob(record: AgentQueueRecord, job: AgentJobRecord): AgentQueueRecord {
        val latest = store.resolve(record.id) ?: record
        val now = clock()
        val state = when (job.status) {
            AgentJobStatus.COMPLETED -> AgentQueueState.COMPLETED
            AgentJobStatus.REFUSED -> AgentQueueState.REFUSED
            AgentJobStatus.FAILED,
            AgentJobStatus.PLANNING,
            AgentJobStatus.PATCHING,
            AgentJobStatus.APPLYING,
            AgentJobStatus.REPAIRING -> AgentQueueState.FAILED
        }
        return store.update(
            copyFromJob(latest, job, AgentQueueCheckpoint.FINALIZED).copy(
                state = state,
                lease = null,
                finishedAt = now,
                failureReason = job.failureReason ?: latest.failureReason,
                finalJobResult = job.result ?: latest.finalJobResult
            ),
            eventType = "finalized",
            previousState = latest.state,
            message = "job ${job.id} finished as ${job.status}"
        )
    }

    private fun finalizeInterrupted(record: AgentQueueRecord, reason: String): AgentQueueRecord {
        val latest = store.resolve(record.id) ?: record
        val now = clock()
        val exhausted = latest.attempts >= latest.maxAttempts
        val state = if (exhausted) AgentQueueState.FAILED else AgentQueueState.RETRY_WAIT
        return store.update(
            latest.copy(
                state = state,
                lease = null,
                failureReason = reason,
                nextEligibleAt = if (state == AgentQueueState.RETRY_WAIT) now.plusSeconds(backoffSeconds(latest.attempts)) else null,
                finishedAt = if (state == AgentQueueState.FAILED) now else null
            ),
            eventType = if (exhausted) "failed" else "retry_wait",
            previousState = latest.state,
            message = reason
        )
    }

    private fun copyFromJob(record: AgentQueueRecord, job: AgentJobRecord?, checkpoint: AgentQueueCheckpoint): AgentQueueRecord {
        if (job == null) return record.copy(checkpoint = checkpoint)
        return record.copy(
            checkpoint = checkpoint,
            jobId = job.id,
            provider = job.provider,
            patchId = job.patchId ?: record.patchId,
            appliedPatchId = job.appliedPatchId ?: record.appliedPatchId,
            verificationId = job.verificationId ?: record.verificationId,
            repairId = job.repairId ?: record.repairId,
            contextExportPath = job.contextExportPath ?: record.contextExportPath,
            finalJobResult = job.result ?: record.finalJobResult,
            sourceEvidence = job.sourceEvidence ?: record.sourceEvidence,
            impactedSymbols = if (job.impactedSymbols.isNotEmpty()) job.impactedSymbols else record.impactedSymbols,
            failureReason = job.failureReason ?: record.failureReason
        )
    }

    private fun leaseSeconds(): Long {
        val raw = System.getenv("ATROPOS_AGENT_LEASE_SECONDS")?.toLongOrNull()
        return (raw ?: AgentQueueDefaults.DEFAULT_LEASE_SECONDS)
            .coerceIn(AgentQueueDefaults.MIN_LEASE_SECONDS, AgentQueueDefaults.MAX_LEASE_SECONDS)
    }

    private fun operationLeaseSeconds(): Long =
        leaseSeconds().coerceAtLeast(30L * 60L).coerceAtMost(AgentQueueDefaults.MAX_LEASE_SECONDS)

    private fun backoffSeconds(attempts: Int): Long =
        (5L * attempts.coerceAtLeast(1)).coerceAtMost(60L)

    private fun rememberQueue(record: AgentQueueRecord, title: String) {
        memoryStore.rememberQueue(
            subjectId = record.id,
            title = title,
            body = buildString {
                appendLine("state=${record.state}")
                appendLine("checkpoint=${record.checkpoint}")
                appendLine("attempts=${record.attempts}/${record.maxAttempts}")
                appendLine("job=${record.jobId ?: "none"}")
                appendLine("provider=${record.provider ?: "none"}")
                appendLine("patch=${record.patchId ?: "none"}")
                appendLine("verification=${record.verificationId ?: "none"}")
                appendLine("source=${record.sourceEvidence ?: "unresolved"}")
                appendLine("impacted=${record.impactedSymbols.joinToString(", ").ifBlank { "none" }}")
                appendLine("failure=${record.failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "queue", record.state.name.lowercase())
        )
    }
}
