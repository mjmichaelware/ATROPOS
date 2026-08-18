package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.time.Instant

enum class GoalTerminalCondition {
    VERIFIED_COMPLETE,
    POLICY_BLOCKED,
    EXTERNAL_INPUT_REQUIRED,
    RETRY_BUDGET_EXHAUSTED,
    CANCELLED,
    TERMINAL_FAILURE
}

enum class GoalRunStatus {
    RUNNING,
    CONTINUING,
    RECOVERY_REQUIRED,
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED
}

data class GoalRunRecord(
    val id: String,
    val goalId: String? = null,
    val projectId: String? = null,
    val dagId: String? = null,
    val atomId: String? = null,
    val task: String,
    val provider: String? = null,
    val status: GoalRunStatus = GoalRunStatus.RUNNING,
    val terminalCondition: GoalTerminalCondition? = null,
    val continuationCount: Int = 0,
    val maxContinuations: Int = 10,
    val lastContinuationAt: Instant? = null,
    val compactState: String? = null,
    val lastProviderResponseId: String? = null,
    val failureReason: String? = null,
    val parentRunId: String? = null,
    val runId: String? = null,
    /** Self-hosting fields */
    val baselineCommit: String? = null,
    val dirtyStateFingerprint: String? = null,
    val activePhase: String? = null,
    val currentNodeId: String? = null,
    val territory: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val retryBudget: Int = 10,
    val lastVerifiedCheckpoint: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant? = null,
    val metaFile: Path
) {
    fun isTerminal(): Boolean = terminalCondition != null || status in setOf(
        GoalRunStatus.COMPLETED, GoalRunStatus.FAILED, GoalRunStatus.CANCELLED, GoalRunStatus.BLOCKED
    )

    /**
     * Whether an operator can pick this run back up.
     *
     * Deliberately not the inverse of [isTerminal]. A run that stopped with
     * `EXTERNAL_INPUT_REQUIRED` is terminal for the *autonomous* loop -- it
     * must not spin on work that is waiting for a human -- and is exactly the
     * run a human then comes back to. Reading resumability off `isTerminal()`
     * meant `/self-host resume` answered "no unfinished self-host goals" for a
     * goal that had stopped specifically to ask for something, which made the
     * durable goal id look pointless: recorded, reported, and unreachable the
     * moment the session ended.
     *
     * `RECOVERY_REQUIRED` is included for the crash case: a process killed
     * mid-run leaves the record needing recovery, and that is the whole reason
     * the record is durable.
     */
    fun isResumable(): Boolean =
        !isTerminal() ||
            terminalCondition == GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED ||
            status == GoalRunStatus.RECOVERY_REQUIRED

    fun canContinue(): Boolean =
        !isTerminal() && continuationCount < maxContinuations

    fun shouldCooldown(): Boolean =
        continuationCount > 0 && continuationCount % 3 == 0

    fun render(): String = buildString {
        val filter = RedactionFilter()
        appendLine("goal run id: $id")
        appendLine("goal id: ${goalId ?: "none"}")
        appendLine("project id: ${projectId ?: "none"}")
        appendLine("dag id: ${dagId ?: "none"}")
        appendLine("atom id: ${atomId ?: "none"}")
        appendLine("task: ${filter.redact(task)}")
        appendLine("provider: ${provider ?: "none"}")
        appendLine("status: $status")
        appendLine("terminal condition: ${terminalCondition ?: "none"}")
        appendLine("continuation count: $continuationCount")
        appendLine("max continuations: $maxContinuations")
        appendLine("last continuation at: ${lastContinuationAt ?: "none"}")
        appendLine("last provider response: ${lastProviderResponseId?.let { it.take(16) } ?: "none"}")
        appendLine("failure reason: ${failureReason?.let(filter::redact) ?: "none"}")
        appendLine("parent run: ${parentRunId ?: "none"}")
        appendLine("baseline commit: ${baselineCommit?.take(12) ?: "none"}")
        appendLine("dirty fingerprint: ${dirtyStateFingerprint?.take(12) ?: "none"}")
        appendLine("active phase: ${activePhase ?: "none"}")
        appendLine("current node: ${currentNodeId ?: "none"}")
        appendLine("territory: ${territory.joinToString(", ").ifEmpty { "none" }}")
        appendLine("evidence: ${evidence.size} entries")
        appendLine("retry budget: $retryBudget")
        appendLine("last verified checkpoint: ${lastVerifiedCheckpoint ?: "none"}")
        appendLine("created at: $createdAt")
        appendLine("updated at: $updatedAt")
        appendLine("finished at: ${finishedAt ?: "none"}")
        appendLine("record file: $metaFile")
    }.trimEnd()

    fun renderSummaryLine(): String = buildString {
        append(id)
        append(" | status=$status")
        terminalCondition?.let { append(" terminal=$it") }
        append(" cont=$continuationCount/$maxContinuations")
        provider?.let { append(" provider=$it") }
        activePhase?.let { append(" phase=$it") }
        currentNodeId?.let { append(" node=$it") }
        baselineCommit?.let { append(" base=${it.take(8)}") }
        failureReason?.let { append(" failure=${it.take(60)}") }
    }
}

data class GoalContinuationRequest(
    val goalRunId: String,
    val compactState: String?,
    val continuationIndex: Int,
    val lastResponseSummary: String?,
    val provider: String?
)

data class GoalContinuationResult(
    val ok: Boolean,
    val message: String,
    val record: GoalRunRecord? = null,
    val terminalCondition: GoalTerminalCondition? = null
)

data class GoalRunListResult(
    val runs: List<GoalRunRecord>,
    val message: String
)
