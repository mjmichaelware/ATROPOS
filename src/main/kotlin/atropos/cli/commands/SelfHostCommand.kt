package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.SelfHostGoalService
import atropos.core.dag.DagExecutionService
import atropos.core.journal.EventJournalService
import atropos.core.verification.VerifiedCompletionGate
import java.nio.file.Path

class SelfHostCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val selfHostService: SelfHostGoalService = SelfHostGoalService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(config, repoRoot)
) : AgentCommandHandler {

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        val normalized = when {
            tokens.size >= 2 && tokens[0].lowercase() == "/agent" && tokens[1].lowercase() == "self-host" -> tokens.drop(1)
            else -> tokens
        }
        if (normalized.size < 2 || normalized[0].lowercase() != "self-host") {
            return AgentCommandOutcome.Invalid(usage())
        }
        return when (normalized[1].lowercase()) {
            "start" -> handleStart(normalized.drop(2))
            "status" -> handleStatus(normalized.drop(2))
            "watch" -> handleWatch(normalized.drop(2))
            "resume" -> handleResume(normalized.drop(2))
            "stop" -> handleStop(normalized.drop(2))
            "verify" -> handleVerify(normalized.drop(2))
            "history" -> handleHistory()
            "learned" -> handleLearned()
            "benchmark" -> handleBenchmark()
            else -> AgentCommandOutcome.Invalid(usage())
        }
    }

    private fun usage(): String =
        buildString {
            appendLine("usage: /agent self-host <command>")
            appendLine("  start <goal-name> [--phase <phase>]")
            appendLine("  status [goal-id]")
            appendLine("  watch [goal-id]")
            appendLine("  resume [goal-id]")
            appendLine("  stop [goal-id]")
            appendLine("  verify [goal-id]")
            appendLine("  history")
            appendLine("  learned")
            append("  benchmark")
        }

    private fun handleStart(args: List<String>): AgentCommandOutcome {
        val phaseIndex = args.indexOf("--phase")
        val phase = if (phaseIndex >= 0) args.getOrNull(phaseIndex + 1) ?: "1" else "1"
        val goalTokens = if (phaseIndex >= 0) {
            args.filterIndexed { index, _ -> index != phaseIndex && index != phaseIndex + 1 }
        } else {
            args
        }
        val goalName = goalTokens.joinToString(" ").ifBlank {
            return AgentCommandOutcome.Invalid("usage: /agent self-host start <goal-name> [--phase <phase>]")
        }

        val result = selfHostService.startGoal(goalName, phase)
        if (!result.ok) {
            ui.renderError(result.message)
            return AgentCommandOutcome.Invalid(result.message)
        }
        val startedGoal = result.goal?.record
        if (startedGoal != null) {
            journal.record(
                goalId = startedGoal.id,
                runId = startedGoal.id,
                category = atropos.core.journal.EventCategory.LIFECYCLE,
                payload = "started: phase=${startedGoal.activePhase ?: phase} task=${startedGoal.task}"
            )
        }
        ui.renderNotice("self-host goal started: ${startedGoal?.id}")
        return AgentCommandOutcome.Completed(result.message)
    }

    private fun handleStatus(args: List<String>): AgentCommandOutcome {
        val requestedGoalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val selected = selfHostService.resolveStatusGoal(requestedGoalId)
        if (!selected.ok) {
            if (requestedGoalId != null) {
                return AgentCommandOutcome.Invalid(selected.message)
            }
            val history = selfHostService.history(5)
            val text = buildString {
                appendLine("no active self-host goals")
                if (history.isNotEmpty()) {
                    appendLine()
                    appendLine("recent history:")
                    history.forEach { appendLine("  ${it.renderSummaryLine()}") }
                }
            }.trimEnd()
            ui.renderNotice("SELF-HOST STATUS\n$text")
            return AgentCommandOutcome.Completed(text)
        }

        val status = selfHostService.status(selected.goal?.record?.id)
        val goals = selfHostService.loadUnfinishedGoals()
        val text = buildString {
            if (requestedGoalId != null || goals.isEmpty()) {
                append(renderStatusDetails(status))
            } else {
                appendLine("active self-host goals:")
                goals.forEach { goal ->
                    val marker = if (goal.record.id == status.goalId) "*" else " "
                    appendLine("$marker ${goal.record.renderSummaryLine()}")
                    val status = selfHostService.status(goal.record.id)
                    appendLine("  phase: ${status.phase ?: "none"} node: ${status.currentNodeId ?: "none"}")
                    status.dagStatus?.let { dag ->
                        appendLine("  DAG: ${dag.completedNodes}/${dag.totalNodes} completed ${dag.failedNodes} failed ${dag.blockedNodes} blocked")
                        if (dag.readyNodes.isNotEmpty()) {
                            appendLine("  ready: ${dag.readyNodes.joinToString(", ")}")
                        }
                    }
                }
                appendLine()
                appendLine("selected status:")
                append(renderStatusDetails(status))
            }
        }.trimEnd()
        ui.renderNotice("SELF-HOST STATUS\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleWatch(args: List<String>): AgentCommandOutcome {
        val selected = selfHostService.resolveWatchGoal(args.getOrNull(0)?.takeIf { it.isNotBlank() })
        if (!selected.ok) {
            return AgentCommandOutcome.Invalid(selected.message)
        }
        val goalId = selected.goal?.record?.id ?: return AgentCommandOutcome.Invalid("no self-host goal selected")
        val events = journal.readEvents(goalId, 20)
        val text = if (events.isNotEmpty()) {
            events.joinToString("\n") { it.render() }
        } else {
            val status = selfHostService.status(goalId)
            buildString {
                appendLine("no events for goal $goalId")
                appendLine("status: ${status.status}")
                appendLine("phase: ${status.phase ?: "none"}")
                appendLine("node: ${status.currentNodeId ?: "none"}")
                append("terminal: ${status.terminalCondition ?: "none"}")
            }
        }
        ui.renderNotice("SELF-HOST WATCH $goalId\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleResume(args: List<String>): AgentCommandOutcome {
        val requestedGoalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val selected = selfHostService.resolveResumableGoal(requestedGoalId)
        if (!selected.ok) {
            return AgentCommandOutcome.Invalid(selected.message)
        }
        val record = selected.goal?.record ?: return AgentCommandOutcome.Invalid("no resumable self-host goal found")

        ui.startSpinner("Resuming self-host goal ${record.id}")
        try {
            val resumed = selfHostService.resumeGoal(record.id, compactState = "self-host resume")
            if (!resumed.ok) {
                ui.renderError("resume: ${resumed.message}")
                return AgentCommandOutcome.Invalid(resumed.message)
            }
            var resumedRecord = resumed.goal?.record ?: record

            val selectResult = selfHostService.selectNextDagNode(resumedRecord.id)
            if (!selectResult.ok) {
                resumedRecord = selfHostService.loadGoal(resumedRecord.id).goal?.record
                    ?: selfHostService.status(resumedRecord.id).let { status ->
                        resumedRecord.copy(
                            status = status.status,
                            terminalCondition = status.terminalCondition,
                            currentNodeId = status.currentNodeId
                        )
                    }
                if (resumedRecord.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE) {
                    val text = "self-host goal ${resumedRecord.id} completed: all DAG nodes done"
                    journal.record(
                        goalId = resumedRecord.id,
                        runId = resumedRecord.id,
                        category = atropos.core.journal.EventCategory.LIFECYCLE,
                        payload = "resumed: terminal=VERIFIED_COMPLETE reason=${resumedRecord.failureReason ?: "all nodes done"}"
                    )
                    ui.renderNotice(text)
                    return AgentCommandOutcome.Completed(text)
                }
                if (resumedRecord.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE) {
                    val text = "self-host goal ${resumedRecord.id} failed: ${resumedRecord.failureReason ?: selectResult.message}"
                    journal.record(
                        goalId = resumedRecord.id,
                        runId = resumedRecord.id,
                        category = atropos.core.journal.EventCategory.LIFECYCLE,
                        payload = "resumed: terminal=TERMINAL_FAILURE reason=${resumedRecord.failureReason ?: selectResult.message}"
                    )
                    ui.renderError(text)
                    return AgentCommandOutcome.Invalid(text)
                }
                ui.renderError("resume: ${selectResult.message}")
                return AgentCommandOutcome.Invalid(selectResult.message)
            }

            val currentNodeId = selectResult.goal?.record?.currentNodeId ?: return AgentCommandOutcome.Invalid("no node selected")
            val dagId = resumedRecord.dagId ?: return AgentCommandOutcome.Invalid("no DAG assigned")
            dagService.evaluateDag(dagId)
            val dag = dagService.readDag(dagId)

            val completed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.COMPLETE } ?: 0
            val failed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.FAILED } ?: 0
            val total = dag?.nodes?.size ?: 0

            val text = buildString {
                appendLine("resumed goal: ${resumedRecord.id}")
                appendLine("phase: ${resumedRecord.activePhase}")
                appendLine("current node: $currentNodeId")
                appendLine("DAG: $completed/$total completed, $failed failed")

                // Check for false completions
                val falseCompletions = completionGate.detectFalseCompletions(dagId)
                if (falseCompletions.isNotEmpty()) {
                    appendLine("WARNING: false completions detected: ${falseCompletions.joinToString(", ")}")
                }

                if (total > 0 && completed + failed == total) {
                    if (failed == 0) {
                        selfHostService.completeGoal(resumedRecord.id, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes done")
                        appendLine("goal complete: all DAG nodes verified")
                    } else {
                        selfHostService.completeGoal(resumedRecord.id, GoalTerminalCondition.TERMINAL_FAILURE, "$failed failed nodes")
                        appendLine("goal failed: $failed nodes failed")
                    }
                }
            }
            ui.renderNotice("SELF-HOST RESUME\n$text")
            journal.record(goalId = resumedRecord.id, runId = resumedRecord.id,
                category = atropos.core.journal.EventCategory.LIFECYCLE,
                payload = "resumed: node=$currentNodeId completed=$completed failed=$failed")
            return AgentCommandOutcome.Completed(text)
        } catch (e: Exception) {
            val text = "resume failed: ${e.message}"
            ui.renderError(text)
            return AgentCommandOutcome.Invalid(text)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun handleStop(args: List<String>): AgentCommandOutcome {
        val selected = selfHostService.resolveStoppableGoal(args.getOrNull(0)?.takeIf { it.isNotBlank() })
        if (!selected.ok) {
            return AgentCommandOutcome.Invalid(selected.message)
        }
        val goalId = selected.goal?.record?.id ?: return AgentCommandOutcome.Invalid("no stoppable self-host goal selected")
        val result = selfHostService.completeGoal(goalId, GoalTerminalCondition.CANCELLED, "operator stopped")
        if (!result.ok) {
            ui.renderError(result.message)
            return AgentCommandOutcome.Invalid(result.message)
        }
        journal.record(
            goalId = goalId,
            runId = goalId,
            category = atropos.core.journal.EventCategory.CANCELLATION,
            payload = "stopped: operator requested cancellation"
        )
        ui.renderNotice("self-host goal $goalId stopped")
        return AgentCommandOutcome.Completed(result.message)
    }

    private fun handleVerify(args: List<String>): AgentCommandOutcome {
        val goalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val selected = selfHostService.resolveStatusGoal(goalId)
        if (!selected.ok) {
            return AgentCommandOutcome.Invalid(selected.message)
        }
        val status = selfHostService.status(selected.goal?.record?.id)

        val dagStatus = status.dagStatus
        val text = buildString {
            appendLine("goal: ${status.goalId}")
            appendLine("status: ${status.status}")
            appendLine("terminal: ${status.terminalCondition ?: "none"}")
            appendLine("phase: ${status.phase ?: "none"}")
            if (dagStatus != null) {
                appendLine("DAG: ${dagStatus.completedNodes}/${dagStatus.totalNodes} completed")
                appendLine("failed: ${dagStatus.failedNodes}")
                appendLine("blocked: ${dagStatus.blockedNodes}")
                appendLine("pending: ${dagStatus.pendingNodes}")
                appendLine("running: ${dagStatus.runningNodes}")
                appendLine("ready: ${dagStatus.readyNodes.joinToString(", ").ifEmpty { "none" }}")

                val dag = dagService.readDag(dagStatus.dagId)
                if (dag != null) {
                    val falseCompletions = completionGate.detectFalseCompletions(dagStatus.dagId)
                    if (falseCompletions.isNotEmpty()) {
                        appendLine("FALSE COMPLETIONS: ${falseCompletions.joinToString(", ")}")
                    }
                }
            }
        }
        ui.renderNotice("SELF-HOST VERIFY\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleHistory(): AgentCommandOutcome {
        val goals = selfHostService.history(20)
        val text = goals.joinToString("\n") { it.renderSummaryLine() }.ifEmpty { "no self-host goals" }
        ui.renderNotice("SELF-HOST HISTORY\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleLearned(): AgentCommandOutcome {
        val experiences = selfHostService.learned(20)
        val text = if (experiences.isEmpty()) {
            "no learned experiences yet"
        } else {
            experiences.joinToString("\n") { "${it.title}: ${it.body.take(120)}" }
        }
        ui.renderNotice("SELF-HOST LEARNED\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleBenchmark(): AgentCommandOutcome {
        val benchmark = selfHostService.benchmark()

        val text = buildString {
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
        ui.renderNotice("SELF-HOST BENCHMARK\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun renderStatusDetails(status: atropos.core.agent.SelfHostStatus): String = buildString {
        appendLine("${status.goalId}: ${status.status} (phase ${status.phase ?: "?"})")
        appendLine("phase: ${status.phase ?: "none"}")
        appendLine("node: ${status.currentNodeId ?: "none"}")
        appendLine("terminal: ${status.terminalCondition ?: "none"}")
        status.dagStatus?.let { dag ->
            appendLine("DAG: ${dag.completedNodes}/${dag.totalNodes} completed ${dag.failedNodes} failed ${dag.blockedNodes} blocked")
            if (dag.readyNodes.isNotEmpty()) {
                appendLine("ready: ${dag.readyNodes.joinToString(", ")}")
            }
        }
    }.trimEnd()
}
