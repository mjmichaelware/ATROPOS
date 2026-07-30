package atropos.cli.commands

import atropos.core.agent.GoalRunRecord
import atropos.core.agent.SelfHostAutonomousRunResult
import atropos.core.agent.SelfHostBenchmark
import atropos.core.agent.SelfHostEvidenceBundleResult
import atropos.core.agent.SelfHostNextAction
import atropos.core.agent.SelfHostPromotionResult
import atropos.core.agent.SelfHostStatus

object SelfHostCommandText {
    fun usage(): String =
        buildString {
            appendLine("usage: /agent self-host <command>")
            appendLine("  run <natural-language self-host goal>")
            appendLine("  start <goal-name> [--phase <phase>]")
            appendLine("  status [goal-id]")
            appendLine("  watch [goal-id]")
            appendLine("  resume [goal-id]")
            appendLine("  recover [goal-id]")
            appendLine("  next [goal-id]")
            appendLine("  stop [goal-id]")
            appendLine("  verify [goal-id]")
            appendLine("  promote <goal-id> <candidate-jar> <target-jar> [node-id]")
            appendLine("  export-evidence <goal-id>")
            appendLine("  history")
            appendLine("  learned")
            append("  benchmark")
        }

    fun noActiveGoals(history: List<GoalRunRecord>): String =
        buildString {
            appendLine("no active self-host goals")
            if (history.isNotEmpty()) {
                appendLine()
                appendLine("recent history:")
                history.forEach { appendLine("  ${it.renderSummaryLine()}") }
            }
        }.trimEnd()

    fun statusList(
        selectedStatus: SelfHostStatus,
        goals: List<SelfHostStatus>
    ): String =
        buildString {
            appendLine("active self-host goals:")
            goals.forEach { status ->
                val marker = if (status.goalId == selectedStatus.goalId) "*" else " "
                appendLine("$marker ${status.goalId}: ${status.status} (phase ${status.phase ?: "?"})")
                appendLine("  phase: ${status.phase ?: "none"} node: ${status.currentNodeId ?: "none"}")
                status.dagStatus?.let { dag ->
                    appendLine("  DAG: ${dag.completedNodes}/${dag.totalNodes} completed ${dag.failedNodes} failed ${dag.blockedNodes} blocked")
                    if (dag.readyNodes.isNotEmpty()) appendLine("  ready: ${dag.readyNodes.joinToString(", ")}")
                }
            }
            appendLine()
            appendLine("selected status:")
            append(statusDetails(selectedStatus))
        }.trimEnd()

    fun statusDetails(status: SelfHostStatus): String = buildString {
        appendLine("${status.goalId}: ${status.status} (phase ${status.phase ?: "?"})")
        appendLine("phase: ${status.phase ?: "none"}")
        appendLine("node: ${status.currentNodeId ?: "none"}")
        appendLine("terminal: ${status.terminalCondition ?: "none"}")
        status.dagStatus?.let { dag ->
            appendLine("DAG: ${dag.completedNodes}/${dag.totalNodes} completed ${dag.failedNodes} failed ${dag.blockedNodes} blocked")
            if (dag.readyNodes.isNotEmpty()) appendLine("ready: ${dag.readyNodes.joinToString(", ")}")
        }
    }.trimEnd()

    fun watchFallback(goalId: String, status: SelfHostStatus): String =
        buildString {
            appendLine("no events for goal $goalId")
            appendLine("status: ${status.status}")
            appendLine("phase: ${status.phase ?: "none"}")
            appendLine("node: ${status.currentNodeId ?: "none"}")
            append("terminal: ${status.terminalCondition ?: "none"}")
        }

    fun resume(
        resumedRecord: GoalRunRecord,
        currentNodeId: String,
        completed: Int,
        total: Int,
        failed: Int,
        blocked: Int,
        advanceMessage: String,
        falseCompletions: List<String>
    ): String =
        buildString {
            appendLine("resumed goal: ${resumedRecord.id}")
            appendLine("phase: ${resumedRecord.activePhase}")
            appendLine("current node: $currentNodeId")
            appendLine("DAG: $completed/$total completed, $failed failed, $blocked blocked")
            appendLine("advance: $advanceMessage")
            if (falseCompletions.isNotEmpty()) {
                appendLine("WARNING: false completions detected: ${falseCompletions.joinToString(", ")}")
            }
        }

    fun run(result: SelfHostAutonomousRunResult): String =
        buildString {
            appendLine(result.message)
            appendLine("goal: ${result.goal?.record?.id ?: "none"}")
            appendLine("status: ${result.goal?.record?.status ?: "none"}")
            appendLine("terminal: ${result.goal?.record?.terminalCondition ?: "none"}")
            appendLine("promotion: ${result.promotion?.message ?: "not attempted"}")
            result.evidenceBundle?.let {
                appendLine("evidence markdown: ${it.markdownPath ?: "none"}")
                appendLine("evidence json: ${it.jsonPath ?: "none"}")
            }
            appendLine("steps:")
            result.steps.forEach { appendLine("  - $it") }
        }.trimEnd()

    fun recover(message: String, record: GoalRunRecord?, next: String?): String =
        buildString {
            appendLine(message)
            if (record != null) {
                appendLine("goal: ${record.id}")
                appendLine("phase: ${record.activePhase ?: "none"}")
                appendLine("node: ${record.currentNodeId ?: "none"}")
                appendLine("status: ${record.status}")
                appendLine("next: ${next ?: "none"}")
            }
        }.trimEnd()

    fun next(next: SelfHostNextAction): String =
        buildString {
            appendLine("next: ${next.kind}")
            appendLine("goal: ${next.goalId ?: "none"}")
            appendLine("node: ${next.nodeId ?: "none"}")
            append("reason: ${next.reason}")
        }

    fun verify(status: SelfHostStatus, falseCompletions: List<String>): String =
        buildString {
            appendLine("goal: ${status.goalId}")
            appendLine("status: ${status.status}")
            appendLine("terminal: ${status.terminalCondition ?: "none"}")
            appendLine("phase: ${status.phase ?: "none"}")
            status.dagStatus?.let { dagStatus ->
                appendLine("DAG: ${dagStatus.completedNodes}/${dagStatus.totalNodes} completed")
                appendLine("failed: ${dagStatus.failedNodes}")
                appendLine("blocked: ${dagStatus.blockedNodes}")
                appendLine("pending: ${dagStatus.pendingNodes}")
                appendLine("running: ${dagStatus.runningNodes}")
                appendLine("ready: ${dagStatus.readyNodes.joinToString(", ").ifEmpty { "none" }}")
                if (falseCompletions.isNotEmpty()) {
                    appendLine("FALSE COMPLETIONS: ${falseCompletions.joinToString(", ")}")
                }
            }
        }

    fun promote(result: SelfHostPromotionResult, fallbackGoalId: String): String =
        buildString {
            appendLine(result.message)
            appendLine("promoted: ${result.promoted}")
            appendLine("goal: ${result.goal?.record?.id ?: fallbackGoalId}")
            appendLine("gate: ${result.gateReport?.message ?: "not evaluated"}")
            result.jarSwap?.let {
                appendLine("candidate: ${it.candidateJar}")
                appendLine("target: ${it.targetJar}")
                appendLine("backup: ${it.backupJar ?: "none"}")
            }
        }.trimEnd()

    fun evidence(result: SelfHostEvidenceBundleResult): String =
        buildString {
            appendLine(result.message)
            appendLine("markdown: ${result.markdownPath ?: "none"}")
            appendLine("markdown sha256: ${result.markdownSha256 ?: "none"}")
            appendLine("json: ${result.jsonPath ?: "none"}")
            appendLine("json sha256: ${result.jsonSha256 ?: "none"}")
        }.trimEnd()

    fun learned(experiences: List<atropos.core.memory.MemoryRecord>): String =
        if (experiences.isEmpty()) {
            "no learned experiences yet"
        } else {
            experiences.joinToString("\n") { "${it.title}: ${it.body.take(120)}" }
        }

    fun benchmark(benchmark: SelfHostBenchmark): String =
        buildString {
            appendLine("self-host benchmark:")
            appendLine("  total goals: ${benchmark.totalGoals}")
            appendLine("  completed: ${benchmark.completed}")
            appendLine("  failed: ${benchmark.failed}")
            appendLine("  cancelled: ${benchmark.cancelled}")
            appendLine("  recovery required: ${benchmark.recoveryRequired}")
            appendLine("  total continuations: ${benchmark.totalContinuations}")
            appendLine("  avg continuations/goal: ${"%.1f".format(benchmark.avgContinuations)}")
            appendLine("  batch evidence status: ${benchmark.status}")
        }
}
