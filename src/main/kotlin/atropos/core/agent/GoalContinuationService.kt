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
        val record = store.resolve(goalRunId)
            ?: return GoalContinuationResult(false, "goal run not found: $goalRunId")
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

        if (record.shouldCooldown()) {
            val cooldownUntil = (record.lastContinuationAt ?: record.createdAt).plusSeconds(cooldownSeconds)
            if (cooldownUntil.isAfter(clock())) {
                return GoalContinuationResult(false, "cooldown active until $cooldownUntil", record)
            }
        }

        if (record.lastProviderResponseId != null && request.lastResponseSummary != null) {
            if (record.lastProviderResponseId == request.lastResponseSummary.take(64)) {
                return GoalContinuationResult(false, "duplicate continuation prevented (same response id)", record)
            }
        }

        val now = clock()
        val nextIndex = record.continuationCount + 1
        val updated = store.update(
            record.copy(
                status = GoalRunStatus.CONTINUING,
                continuationCount = nextIndex,
                compactState = request.compactState ?: record.compactState,
                lastProviderResponseId = request.lastResponseSummary?.take(64),
                lastContinuationAt = now,
                provider = request.provider ?: record.provider,
                updatedAt = now
            )
        )
        rememberGoal(updated, "continued #$nextIndex")
        return GoalContinuationResult(true, "continuation #$nextIndex", updated)
    }

    fun completeRun(goalRunId: String, condition: GoalTerminalCondition, reason: String? = null): GoalContinuationResult {
        val record = store.resolve(goalRunId)
            ?: return GoalContinuationResult(false, "goal run not found: $goalRunId")
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
                appendLine("failure=${record.failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "goal", record.status.name.lowercase()),
            subjectType = "goal_run",
            subjectId = record.id
        )
    }
}
