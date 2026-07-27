package atropos.core.agent

import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.time.Instant

class GoalContinuationService(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: GoalRunStore = GoalRunStore(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val cooldownSeconds = 30L
    private val duplicateWindowSeconds = 10L

    fun startRun(task: String, provider: String? = null, parentRunId: String? = null): GoalRunRecord {
        val record = store.createGoalRun(task, provider, parentRunId)
        rememberGoal(record, "started")
        return record
    }

    fun continueRun(goalRunId: String, request: GoalContinuationRequest): GoalContinuationResult {
        if (request.goalRunId != goalRunId) {
            return GoalContinuationResult(
                false,
                "goal run id mismatch: expected $goalRunId but received ${request.goalRunId}"
            )
        }
        val record = store.resolve(goalRunId)
            ?: return GoalContinuationResult(false, "goal run not found: $goalRunId")
        val now = clock()
        if (record.isTerminal()) {
            return GoalContinuationResult(false, "goal run already terminal: ${record.terminalCondition}", record)
        }
        if (!record.canContinue()) {
            val terminal = store.update(
                record.copy(
                    status = GoalRunStatus.FAILED,
                    terminalCondition = GoalTerminalCondition.RETRY_BUDGET_EXHAUSTED,
                    finishedAt = clock(),
                    failureReason = "continuation limit reached (${record.maxContinuations})"
                )
            )
            rememberGoal(terminal, "retry budget exhausted")
            return GoalContinuationResult(false, "retry budget exhausted", terminal, GoalTerminalCondition.RETRY_BUDGET_EXHAUSTED)
        }
        val expectedContinuationIndex = record.continuationCount + 1
        if (request.continuationIndex != expectedContinuationIndex) {
            return GoalContinuationResult(
                false,
                "continuation index mismatch: expected $expectedContinuationIndex but received ${request.continuationIndex}",
                record
            )
        }

        if (record.shouldCooldown()) {
            val cooldownUntil = (record.lastContinuationAt ?: record.createdAt).plusSeconds(cooldownSeconds)
            if (cooldownUntil.isAfter(now)) {
                return GoalContinuationResult(false, "cooldown active until $cooldownUntil", record)
            }
        }

        if (record.lastProviderResponseId != null && request.lastResponseSummary != null) {
            val duplicateWindowUntil = (record.lastContinuationAt ?: record.updatedAt).plusSeconds(duplicateWindowSeconds)
            if (record.lastProviderResponseId == request.lastResponseSummary.take(64) && duplicateWindowUntil.isAfter(now)) {
                return GoalContinuationResult(false, "duplicate continuation prevented (same response id)", record)
            }
        }
        val nextIndex = expectedContinuationIndex
        val resumedFromRecovery = record.status == GoalRunStatus.RECOVERY_REQUIRED
        val resumedEvidence = if (resumedFromRecovery) {
            listOf(
                buildString {
                    append("recovery_resumed_at=").append(now)
                    record.activePhase?.takeIf { it.isNotBlank() }?.let { append(" phase=").append(it) }
                    record.currentNodeId?.takeIf { it.isNotBlank() }?.let { append(" node=").append(it) }
                    record.lastVerifiedCheckpoint?.takeIf { it.isNotBlank() }?.let { append(" checkpoint=").append(it) }
                }
            )
        } else {
            emptyList()
        }
        val updated = store.update(
            record.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = nextIndex,
                compactState = request.compactState ?: record.compactState,
                lastProviderResponseId = request.lastResponseSummary?.take(64),
                lastContinuationAt = now,
                provider = request.provider ?: record.provider,
                updatedAt = now,
                finishedAt = null,
                failureReason = if (resumedFromRecovery) null else record.failureReason,
                evidence = if (resumedEvidence.isEmpty()) {
                    record.evidence
                } else {
                    (record.evidence + resumedEvidence)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .takeLast(40)
                }
            )
        )
        rememberGoal(updated, if (resumedFromRecovery) "recovery resumed #$nextIndex" else "continued #$nextIndex")
        return GoalContinuationResult(true, "continuation #$nextIndex", updated)
    }

    /**
     * Closes a goal run.
     *
     * `VERIFIED_COMPLETE` is the one condition that asserts the work was
     * *proven* done, so it requires evidence to have been recorded. A run that
     * finished having gathered nothing has verified nothing, and letting it
     * claim verified completion is the fail-closed rule from decision F applied
     * to the goal boundary. Every other terminal condition — blocked,
     * cancelled, failed — is a statement about *not* completing and needs no
     * evidence.
     */
    fun completeRun(goalRunId: String, condition: GoalTerminalCondition, reason: String? = null): GoalContinuationResult {
        val record = store.resolve(goalRunId)
            ?: return GoalContinuationResult(false, "goal run not found: $goalRunId")

        if (condition == GoalTerminalCondition.VERIFIED_COMPLETE) {
            // Recovery bookkeeping proves the run was *interrupted*, not that
            // the work was *done*. `markRecoveryRequired` writes those entries
            // into the same evidence list, so a crashed run would otherwise
            // satisfy the gate on the strength of its own crash.
            val substantive = record.evidence.filterNot(::isRecoveryBookkeeping)
            if (substantive.isEmpty()) {
                val why = if (record.evidence.isEmpty()) {
                    "no evidence was recorded"
                } else {
                    "the only evidence is recovery bookkeeping, which proves interruption, not completion"
                }
                return GoalContinuationResult(false, "goal run $goalRunId cannot be marked verified-complete: $why")
            }
        }

        val now = clock()
        val updated = store.update(
            record.copy(
                status = when (condition) {
                    GoalTerminalCondition.VERIFIED_COMPLETE -> GoalRunStatus.COMPLETED
                    GoalTerminalCondition.POLICY_BLOCKED -> GoalRunStatus.BLOCKED
                    GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED -> GoalRunStatus.BLOCKED
                    GoalTerminalCondition.CANCELLED -> GoalRunStatus.CANCELLED
                    GoalTerminalCondition.RETRY_BUDGET_EXHAUSTED,
                    GoalTerminalCondition.TERMINAL_FAILURE -> GoalRunStatus.FAILED
                },
                terminalCondition = condition,
                finishedAt = now,
                failureReason = reason
            )
        )
        rememberGoal(updated, "completed: $condition")
        return GoalContinuationResult(true, "goal run completed: $condition", updated, condition)
    }

    /**
     * Entries written by the recovery path rather than by the work itself.
     *
     * Kept in one place so [completeRun] and [markRecoveryRequired] cannot
     * drift apart about what counts as proof of completion.
     */
    private fun isRecoveryBookkeeping(entry: String): Boolean {
        val normalized = entry.trim().lowercase()
        return normalized.startsWith("recovery_required_at=") ||
            normalized.startsWith("recovery=") ||
            normalized.startsWith("recoveredat=") ||
            normalized.startsWith("continuations=") ||
            normalized.startsWith("phase=") ||
            normalized.startsWith("node=") ||
            normalized.startsWith("checkpoint=")
    }

    fun markRecoveryRequired(goalRunId: String, reason: String, recoveryEvidence: List<String> = emptyList()): GoalContinuationResult {
        val record = store.resolve(goalRunId)
            ?: return GoalContinuationResult(false, "goal run not found: $goalRunId")
        val now = clock()
        val evidenceEntry = buildString {
            append("recovery_required_at=").append(now)
            record.activePhase?.takeIf { it.isNotBlank() }?.let { append(" phase=").append(it) }
            record.currentNodeId?.takeIf { it.isNotBlank() }?.let { append(" node=").append(it) }
            record.lastVerifiedCheckpoint?.takeIf { it.isNotBlank() }?.let { append(" checkpoint=").append(it) }
            append(" reason=").append(reason)
        }
        val updated = store.update(
            record.copy(
                status = GoalRunStatus.RECOVERY_REQUIRED,
                terminalCondition = null,
                finishedAt = null,
                failureReason = reason,
                evidence = (record.evidence + evidenceEntry + recoveryEvidence)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .takeLast(40)
            )
        )
        rememberGoal(updated, "recovery required")
        return GoalContinuationResult(true, "goal run marked recovery required", updated)
    }

    fun listRuns(limit: Int = 20): GoalRunListResult {
        val runs = store.listRuns(limit)
        return GoalRunListResult(runs, "${runs.size} goal run(s)")
    }

    fun resolveRun(reference: String): GoalRunRecord? = store.resolve(reference)

    fun latestRun(): GoalRunRecord? = store.latest()

    private fun rememberGoal(record: GoalRunRecord, title: String) {
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.SESSION,
            title = "goal $title",
            body = buildString {
                appendLine("run=${record.id}")
                appendLine("status=${record.status}")
                record.terminalCondition?.let { appendLine("terminal=$it") }
                appendLine("continuations=${record.continuationCount}")
                appendLine("provider=${record.provider ?: "none"}")
                appendLine("phase=${record.activePhase ?: "none"}")
                appendLine("node=${record.currentNodeId ?: "none"}")
                appendLine("checkpoint=${record.lastVerifiedCheckpoint ?: "none"}")
                appendLine("evidence=${record.evidence.size}")
                appendLine("failure=${record.failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "goal", record.status.name.lowercase()),
            subjectType = "goal_run",
            subjectId = record.id
        )
    }
}
