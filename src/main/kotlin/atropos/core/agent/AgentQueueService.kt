package atropos.core.agent

import atropos.core.AtroposConfig
import java.time.Instant

data class AgentQueueRunResult(
    val queueRecord: AgentQueueRecord?,
    val jobRecord: AgentJobRecord? = null,
    val message: String,
    val ran: Boolean
)

data class AgentQueueBatchResult(
    val results: List<AgentQueueRunResult>,
    val message: String
) {
    fun render(): String = buildString {
        appendLine(message)
        results.forEach { result ->
            val record = result.queueRecord
            appendLine(
                "${record?.id ?: "none"} state=${record?.state ?: "none"} " +
                    "job=${record?.jobId ?: "none"} ran=${result.ran} ${result.message}"
            )
        }
    }.trimEnd()
}

class AgentQueueService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val runService: AgentRunService = AgentRunService(config, collector),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(collector.repoRoot),
    private val store: AgentQueueStore = AgentQueueStore(collector.repoRoot),
    private val recovery: AgentQueueRecovery = AgentQueueRecovery(store),
    private val clock: () -> Instant = { Instant.now() }
) {
    fun enqueue(task: String, smokeCommand: String? = null): AgentQueueRecord {
        val smoke = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        val refusal = smoke?.let { smokeRunner.validate(it) }
        if (refusal != null) {
            return store.createEntry(
                task = task,
                smokeCommand = smoke,
                state = AgentQueueState.REFUSED,
                checkpoint = AgentQueueCheckpoint.FINALIZED,
                provider = "none",
                failureReason = refusal
            )
        }
        return store.createEntry(task = task, smokeCommand = smoke)
    }

    fun list(limit: Int = 20): List<AgentQueueRecord> = store.listEntries(limit)

    fun resolve(reference: String): AgentQueueRecord? = store.resolve(reference)

    fun latest(): AgentQueueRecord? = store.latest()

    fun runNext(activeProviderName: String): AgentQueueRunResult {
        val selected = claimNextEligible()
            ?: return AgentQueueRunResult(null, message = "queue empty or selection lock busy", ran = false)
        if (selected.state == AgentQueueState.FAILED) {
            return AgentQueueRunResult(selected, message = selected.failureReason ?: "queue entry failed before lease", ran = false)
        }
        return executeClaimed(activeProviderName, selected)
    }

    fun runMax(activeProviderName: String, maxCount: Int): AgentQueueBatchResult {
        if (maxCount !in 1..AgentQueueDefaults.MAX_RUN_COUNT) {
            return AgentQueueBatchResult(
                emptyList(),
                "invalid --max: expected 1 through ${AgentQueueDefaults.MAX_RUN_COUNT}"
            )
        }

        val results = mutableListOf<AgentQueueRunResult>()
        for (index in 0 until maxCount) {
            val result = runNext(activeProviderName)
            results += result
            if (!result.ran || result.queueRecord?.state in setOf(AgentQueueState.FAILED, AgentQueueState.REFUSED)) {
                break
            }
        }
        val ran = results.count { it.ran }
        return AgentQueueBatchResult(results, "queue run processed $ran item(s), limit $maxCount")
    }

    fun resume(activeProviderName: String, reference: String): AgentQueueRunResult {
        recovery.recover()
        val record = store.resolve(reference)
            ?: return AgentQueueRunResult(null, message = "queue entry not found: $reference", ran = false)
        if (record.state.terminal) {
            return AgentQueueRunResult(record, message = "queue entry is terminal: ${record.state}; resume refused", ran = false)
        }
        val liveLease = record.lease?.takeIf { it.isLive(clock()) }
        if (liveLease != null && record.state in setOf(AgentQueueState.LEASED, AgentQueueState.RUNNING)) {
            return AgentQueueRunResult(record, message = "queue entry has a live lease owned by ${liveLease.owner}", ran = false)
        }
        if (record.checkpoint.ordinal >= AgentQueueCheckpoint.PATCH_APPLIED.ordinal) {
            val reviewed = store.update(
                record.copy(
                    failureReason = "operator review required before resume from checkpoint ${record.checkpoint}"
                ),
                eventType = "resume_refused",
                previousState = record.state,
                message = "operator review required from ${record.checkpoint}"
            )
            return AgentQueueRunResult(reviewed, message = "operator review required from ${record.checkpoint}", ran = false)
        }

        val claimed = claimSpecific(record.id)
            ?: return AgentQueueRunResult(record, message = "unable to acquire lease for resume", ran = false)
        return executeClaimed(activeProviderName, claimed)
    }

    fun cancel(reference: String, reason: String = "operator cancelled queue entry"): AgentQueueRecord? {
        val record = store.resolve(reference) ?: return null
        return store.cancel(record, reason)
    }

    fun recover(): AgentQueueRecoveryResult = recovery.recover()

    fun storageSummary(): String = buildString {
        appendLine("root: ${store.queueRoot()}")
        appendLine("entries: ${store.entriesDirectory()}/<queue-id>.meta")
        appendLine("events: ${store.eventsDirectory()}/<queue-id>.events")
        appendLine("selection lock: ${store.locksDirectory()}/queue.lock")
    }.trimEnd()

    private fun claimNextEligible(): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            val now = clock()
            val candidate = store.allEntries()
                .filter { AgentQueueTransitions.isSelectable(it, now) }
                .sortedBy { it.id }
                .firstOrNull()
                ?: return@withSelectionLock null
            store.acquireLease(candidate.id, store.ownerId(), leaseSeconds()).record
        }
    }

    private fun claimSpecific(queueId: String): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            store.acquireLease(queueId, store.ownerId(), leaseSeconds()).record
        }
    }

    private fun executeClaimed(activeProviderName: String, claimed: AgentQueueRecord): AgentQueueRunResult {
        var queueRecord = store.markRunning(claimed)
        val leaseToken = queueRecord.lease?.token

        fun sync(checkpoint: AgentQueueCheckpoint, job: AgentJobRecord?, message: String, duration: Long = leaseSeconds()) {
            val heartbeat = store.heartbeat(queueRecord.id, leaseToken, checkpoint, duration) ?: queueRecord
            val synced = heartbeat.copyFromJob(job, checkpoint)
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
            val job = runService.run(activeProviderName, queueRecord.task, queueRecord.smokeCommand, hooks)
            val terminal = finalizeFromJob(queueRecord, job)
            AgentQueueRunResult(terminal, job, "queue entry finalized as ${terminal.state}", ran = true)
        } catch (_: AgentRunCancelledException) {
            AgentQueueRunResult(queueRecord, null, "queue entry cancelled before next stage", ran = true)
        } catch (failure: Exception) {
            val failed = finalizeInterrupted(queueRecord, failure.message ?: failure.javaClass.simpleName)
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
            latest.copyFromJob(job, AgentQueueCheckpoint.FINALIZED).copy(
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

    private fun AgentQueueRecord.copyFromJob(job: AgentJobRecord?, checkpoint: AgentQueueCheckpoint): AgentQueueRecord {
        if (job == null) return copy(checkpoint = checkpoint)
        return copy(
            checkpoint = checkpoint,
            jobId = job.id,
            provider = job.provider,
            patchId = job.patchId ?: patchId,
            appliedPatchId = job.appliedPatchId ?: appliedPatchId,
            verificationId = job.verificationId ?: verificationId,
            repairId = job.repairId ?: repairId,
            contextExportPath = job.contextExportPath ?: contextExportPath,
            finalJobResult = job.result ?: finalJobResult,
            failureReason = job.failureReason ?: failureReason
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
}
