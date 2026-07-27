package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.agent.GoalRunRecord
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.SelfHostGoalService
import atropos.core.agent.SelfHostResult
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.journal.EventJournalService
import atropos.core.memory.LocalMemoryStore
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Path

class SelfHostCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val selfHostService: SelfHostGoalService = SelfHostGoalService(repoRoot),
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(config, repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
) : AgentCommandHandler {

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        if (tokens.size < 2 || tokens[0].lowercase() != "self-host") {
            return AgentCommandOutcome.Invalid(usage())
        }
        return when (tokens[1].lowercase()) {
            "start" -> handleStart(tokens.drop(2))
            "status" -> handleStatus()
            "watch" -> handleWatch(tokens.drop(2))
            "resume" -> handleResume(tokens.drop(2))
            "stop" -> handleStop(tokens.drop(2))
            "verify" -> handleVerify(tokens.drop(2))
            "history" -> handleHistory()
            "learned" -> handleLearned()
            "benchmark" -> handleBenchmark()
            else -> AgentCommandOutcome.Invalid(usage())
        }
    }

    private fun usage(): String =
        "usage: /agent self-host [start|status|watch|resume|stop|verify|history|learned|benchmark]"

    private fun handleStart(args: List<String>): AgentCommandOutcome {
        val goalName = args.joinToString(" ").ifBlank {
            return AgentCommandOutcome.Invalid("usage: /agent self-host start <goal-name> [--phase <phase>]")
        }
        val phaseIndex = args.indexOf("--phase")
        val phase = if (phaseIndex >= 0) args.getOrNull(phaseIndex + 1) ?: "1" else "1"

        val result = selfHostService.startGoal(goalName, phase)
        if (!result.ok) {
            ui.renderError(result.message)
            return AgentCommandOutcome.Invalid(result.message)
        }
        ui.renderNotice("self-host goal started: ${result.goal?.record?.id}")
        return AgentCommandOutcome.Completed(result.message)
    }

    private fun handleStatus(): AgentCommandOutcome {
        val goals = selfHostService.loadUnfinishedGoals()
        val text = buildString {
            if (goals.isEmpty()) {
                appendLine("no active self-host goals")
                val history = selfHostService.history(5)
                if (history.isNotEmpty()) {
                    appendLine()
                    appendLine("recent history:")
                    history.forEach { appendLine("  ${it.renderSummaryLine()}") }
                }
            } else {
                appendLine("active self-host goals:")
                goals.forEach { goal ->
                    appendLine("  ${goal.record.renderSummaryLine()}")
                    val status = selfHostService.status(goal.record.id)
                    appendLine("  phase: ${status.phase ?: "none"} node: ${status.currentNodeId ?: "none"}")
                    status.dagStatus?.let { dag ->
                        appendLine("  DAG: ${dag.completedNodes}/${dag.totalNodes} completed ${dag.failedNodes} failed ${dag.blockedNodes} blocked")
                        if (dag.readyNodes.isNotEmpty()) {
                            appendLine("  ready: ${dag.readyNodes.joinToString(", ")}")
                        }
                    }
                }
            }
        }
        ui.renderNotice("SELF-HOST STATUS\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleWatch(args: List<String>): AgentCommandOutcome {
        val goalId = args.getOrNull(0) ?: selfHostService.loadUnfinishedGoals().firstOrNull()?.record?.id
        if (goalId == null) {
            return AgentCommandOutcome.Invalid("no self-host goals to watch")
        }
        val events = journal.readEvents(goalId, 20)
        val text = events.joinToString("\n") { it.render() }.ifEmpty { "no events for goal $goalId" }
        ui.renderNotice("SELF-HOST WATCH $goalId\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleResume(args: List<String>): AgentCommandOutcome {
        val goals = selfHostService.loadUnfinishedGoals()
        if (goals.isEmpty()) {
            return AgentCommandOutcome.Invalid("no unfinished self-host goals to resume")
        }
        val goal = goals.first()
        val record = goal.record

        ui.startSpinner("Resuming self-host goal ${record.id}")
        try {
            val selectResult = selfHostService.selectNextDagNode(record.id)
            if (!selectResult.ok) {
                if (record.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE) {
                    val text = "self-host goal ${record.id} completed: all DAG nodes done"
                    ui.renderNotice(text)
                    return AgentCommandOutcome.Completed(text)
                }
                ui.renderError("resume: ${selectResult.message}")
                return AgentCommandOutcome.Invalid(selectResult.message)
            }

            val currentNodeId = selectResult.goal?.record?.currentNodeId ?: return AgentCommandOutcome.Invalid("no node selected")
            val result = dagService.evaluateDag(record.dagId ?: return AgentCommandOutcome.Invalid("no DAG assigned"))
            val dag = dagService.readDag(record.dagId!!)

            val completed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.COMPLETE } ?: 0
            val failed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.FAILED } ?: 0
            val total = dag?.nodes?.size ?: 0

            val text = buildString {
                appendLine("resumed goal: ${record.id}")
                appendLine("phase: ${record.activePhase}")
                appendLine("current node: $currentNodeId")
                appendLine("DAG: $completed/$total completed, $failed failed")

                // Check for false completions
                val falseCompletions = completionGate.detectFalseCompletions(record.dagId)
                if (falseCompletions.isNotEmpty()) {
                    appendLine("WARNING: false completions detected: ${falseCompletions.joinToString(", ")}")
                }

                if (total > 0 && completed + failed == total) {
                    if (failed == 0) {
                        selfHostService.completeGoal(record.id, GoalTerminalCondition.VERIFIED_COMPLETE, "all nodes done")
                        appendLine("goal complete: all DAG nodes verified")
                    } else {
                        selfHostService.completeGoal(record.id, GoalTerminalCondition.TERMINAL_FAILURE, "$failed failed nodes")
                        appendLine("goal failed: $failed nodes failed")
                    }
                }
            }
            ui.renderNotice("SELF-HOST RESUME\n$text")
            journal.record(goalId = record.id, runId = record.id,
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
        val goals = selfHostService.loadUnfinishedGoals()
        if (goals.isEmpty()) {
            return AgentCommandOutcome.Invalid("no active self-host goals to stop")
        }
        val goalId = args.getOrNull(0) ?: goals.first().record.id
        val result = selfHostService.completeGoal(goalId, GoalTerminalCondition.CANCELLED, "operator stopped")
        if (!result.ok) {
            ui.renderError(result.message)
            return AgentCommandOutcome.Invalid(result.message)
        }
        ui.renderNotice("self-host goal $goalId stopped")
        return AgentCommandOutcome.Completed(result.message)
    }

    private fun handleVerify(args: List<String>): AgentCommandOutcome {
        val goalId = args.getOrNull(0)
        val status = selfHostService.status(goalId)

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
        val experiences = memoryStore.findBySubject("selfhost_experience", "", 20)
        val text = if (experiences.isEmpty()) {
            "no learned experiences yet"
        } else {
            experiences.joinToString("\n") { "${it.title}: ${it.body.take(120)}" }
        }
        ui.renderNotice("SELF-HOST LEARNED\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleBenchmark(): AgentCommandOutcome {
        val goals = selfHostService.history(50)
        val completed = goals.count { it.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE }
        val failed = goals.count { it.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE }
        val cancelled = goals.count { it.terminalCondition == GoalTerminalCondition.CANCELLED }
        val totalContinuations = goals.sumOf { it.continuationCount }
        val avgContinuations = if (goals.isNotEmpty()) totalContinuations.toDouble() / goals.size else 0.0

        val text = buildString {
            appendLine("self-host benchmark:")
            appendLine("  total goals: ${goals.size}")
            appendLine("  completed: $completed")
            appendLine("  failed: $failed")
            appendLine("  cancelled: $cancelled")
            appendLine("  total continuations: $totalContinuations")
            appendLine("  avg continuations/goal: ${"%.1f".format(avgContinuations)}")
            appendLine("  crossover status: ${if (completed >= 1) "NOMINALLY_ACHIEVABLE" else "NOT_ACHIEVED"}")
        }
        ui.renderNotice("SELF-HOST BENCHMARK\n$text")
        return AgentCommandOutcome.Completed(text)
    }
}
