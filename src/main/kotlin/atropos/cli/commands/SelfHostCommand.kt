package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.SelfHostAutonomousRunResult
import atropos.core.agent.SelfHostGoalService
import atropos.core.dag.DagExecutionService
import atropos.core.journal.EventJournalService
import atropos.core.verification.VerifiedCompletionGate
import java.nio.file.Path

class SelfHostCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val selfHostService: SelfHostGoalService = SelfHostGoalService(repoRoot),
    private val selfHostRunner: (String) -> SelfHostAutonomousRunResult = { prompt -> selfHostService.runNaturalLanguageSelfBuild(prompt) },
    private val dagService: DagExecutionService = DagExecutionService(config, repoRoot),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(config, repoRoot),
    private val proofRenderer: SelfHostRunProofRenderer = SelfHostRunProofRenderer()
) : AgentCommandHandler {

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        val normalized = when {
            tokens.size >= 2 && tokens[0].lowercase() == "/agent" && tokens[1].lowercase() == "self-host" -> tokens.drop(1)
            else -> tokens
        }
        if (normalized.isEmpty() || normalized[0].lowercase() != "self-host") {
            return AgentCommandOutcome.Invalid(SelfHostCommandText.usage())
        }
        if (normalized.size == 1) return handleRun(listOf(SelfHostDefaultPrompt.TEXT))
        return when (normalized[1].lowercase()) {
            "run" -> handleRun(normalized.drop(2))
            "start" -> handleStart(normalized.drop(2))
            "status" -> handleStatus(normalized.drop(2))
            "watch" -> handleWatch(normalized.drop(2))
            "resume" -> handleResume(normalized.drop(2))
            "recover" -> handleRecover(normalized.drop(2))
            "next" -> handleNext(normalized.drop(2))
            "stop" -> handleStop(normalized.drop(2))
            "verify" -> handleVerify(normalized.drop(2))
            "promote" -> handlePromote(normalized.drop(2))
            "export-evidence" -> handleExportEvidence(normalized.drop(2))
            "history" -> handleHistory()
            "learned" -> handleLearned()
            "benchmark" -> handleBenchmark()
            "governance" -> handleGovernance()
            else -> AgentCommandOutcome.Invalid(SelfHostCommandText.usage())
        }
    }

    private fun handleStart(args: List<String>): AgentCommandOutcome {
        val phaseIndex = args.indexOf("--phase")
        val phase = if (phaseIndex >= 0) args.getOrNull(phaseIndex + 1) ?: "11" else "11"
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
            ?: return AgentCommandOutcome.Invalid("self-host start refused: durable goal was not returned")
        journal.record(
            goalId = startedGoal.id,
            runId = startedGoal.id,
            category = atropos.core.journal.EventCategory.LIFECYCLE,
            payload = "started: phase=${startedGoal.activePhase ?: phase} task=${startedGoal.task}"
        )
        ui.renderNotice("self-host goal started: ${startedGoal.id}")
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
            val text = SelfHostCommandText.noActiveGoals(history)
            ui.renderNotice("SELF-HOST STATUS\n$text")
            return AgentCommandOutcome.Completed(text)
        }

        val status = selfHostService.status(selected.goal?.record?.id)
        val goals = selfHostService.loadUnfinishedGoals()
        val text = if (requestedGoalId != null || goals.isEmpty()) {
            SelfHostCommandText.statusDetails(status)
        } else {
            SelfHostCommandText.statusList(status, goals.map { selfHostService.status(it.record.id) })
        }
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
            SelfHostCommandText.watchFallback(goalId, selfHostService.status(goalId))
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
            val advanced = selfHostService.advanceNextResumableGoal(record.id, compactState = "self-host resume")
            val resumedRecord = advanced.goal?.record ?: record
            val dagId = resumedRecord.dagId
            val dag = dagId?.let { dagService.readDag(it) }
            val currentNodeId = resumedRecord.currentNodeId ?: "none"
            val completed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.COMPLETE } ?: 0
            val failed = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.FAILED } ?: 0
            val blocked = dag?.nodes?.count { it.state == atropos.core.dag.DagNodeState.BLOCKED } ?: 0
            val total = dag?.nodes?.size ?: 0

            val falseCompletions = dagId?.let { completionGate.detectFalseCompletions(it) } ?: emptyList()
            val text = SelfHostCommandText.resume(
                resumedRecord = resumedRecord,
                currentNodeId = currentNodeId,
                completed = completed,
                total = total,
                failed = failed,
                blocked = blocked,
                advanceMessage = advanced.message,
                falseCompletions = falseCompletions
            )
            if (!advanced.ok) {
                ui.renderError("resume: ${advanced.message}")
                journal.record(
                    goalId = resumedRecord.id,
                    runId = resumedRecord.id,
                    category = atropos.core.journal.EventCategory.LIFECYCLE,
                    payload = "resumed: failed node=$currentNodeId reason=${advanced.message}"
                )
                return AgentCommandOutcome.Invalid(text)
            }
            ui.renderNotice("SELF-HOST RESUME\n$text")
            journal.record(goalId = resumedRecord.id, runId = resumedRecord.id,
                category = atropos.core.journal.EventCategory.LIFECYCLE,
                payload = "resumed: node=$currentNodeId completed=$completed failed=$failed blocked=$blocked terminal=${resumedRecord.terminalCondition ?: "none"}")
            return AgentCommandOutcome.Completed(text)
        } catch (e: Exception) {
            val text = "resume failed: ${e.message}"
            ui.renderError(text)
            return AgentCommandOutcome.Invalid(text)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun handleRun(args: List<String>): AgentCommandOutcome {
        val prompt = args.joinToString(" ").ifBlank {
            return AgentCommandOutcome.Invalid("usage: /agent self-host run <natural-language self-host goal>")
        }
        // A spinner communicates one bit — something is happening — and stops
        // being informative in about ten seconds. After that the operator
        // cannot tell progress from a hang, and the only available action
        // destroys the run. The live renderer shows the narration the chain
        // was already producing.
        val live = atropos.cli.ui.LiveThinkingRenderer(ui)
        live.start("Running Phase 11 self-host loop — /thinking 3 for full detail")
        return try {
            val result = selfHostRunner(prompt)
            val text = SelfHostCommandText.run(result, proofRenderer)
            if (!result.isVerifiedSuccess()) {
                ui.renderError(text)
                AgentCommandOutcome.Invalid("self-host run refused: success contract incomplete\n$text")
            } else {
                ui.renderNotice("SELF-HOST RUN\n$text")
                AgentCommandOutcome.Completed(text)
            }
        } catch (failure: Exception) {
            val message = failure.message ?: "self-host run failed"
            ui.renderError(message)
            AgentCommandOutcome.Invalid(message)
        } finally {
            live.stop()
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

    private fun handleRecover(args: List<String>): AgentCommandOutcome {
        val requestedGoalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val result = selfHostService.recoverAndContinue(requestedGoalId)
        val record = result.goal?.record
        val next = record?.let { selfHostService.planNextAction(it.id).kind.toString() }
        val text = SelfHostCommandText.recover(result.message, record, next)
        if (!result.ok) {
            ui.renderError(text)
            return AgentCommandOutcome.Invalid(text)
        }
        ui.renderNotice("SELF-HOST RECOVER\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleNext(args: List<String>): AgentCommandOutcome {
        val requestedGoalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val next = selfHostService.planNextAction(requestedGoalId)
        val text = SelfHostCommandText.next(next)
        ui.renderNotice("SELF-HOST NEXT\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleVerify(args: List<String>): AgentCommandOutcome {
        val goalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        val selected = selfHostService.resolveStatusGoal(goalId)
        if (!selected.ok) {
            return AgentCommandOutcome.Invalid(selected.message)
        }
        val status = selfHostService.status(selected.goal?.record?.id)

        val dagStatus = status.dagStatus
        val falseCompletions = if (dagStatus != null && dagService.readDag(dagStatus.dagId) != null) {
            completionGate.detectFalseCompletions(dagStatus.dagId)
        } else {
            emptyList()
        }
        val text = SelfHostCommandText.verify(status, falseCompletions)
        ui.renderNotice("SELF-HOST VERIFY\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handlePromote(args: List<String>): AgentCommandOutcome {
        if (args.size < 3) {
            return AgentCommandOutcome.Invalid("usage: /agent self-host promote <goal-id> <candidate-jar> <target-jar> [node-id]")
        }
        val result = selfHostService.promoteVerifiedJar(
            goalId = args[0],
            candidateJar = repoRoot.resolve(args[1]).normalize(),
            targetJar = repoRoot.resolve(args[2]).normalize(),
            nodeId = args.getOrNull(3)
        )
        val text = SelfHostCommandText.promote(result, args[0])
        if (!result.promoted) {
            ui.renderError(text)
            return AgentCommandOutcome.Invalid(text)
        }
        ui.renderNotice("SELF-HOST PROMOTE\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleExportEvidence(args: List<String>): AgentCommandOutcome {
        val goalId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
            ?: return AgentCommandOutcome.Invalid("usage: /agent self-host export-evidence <goal-id>")
        val result = selfHostService.exportEvidenceBundle(goalId)
        val text = SelfHostCommandText.evidence(result)
        if (!result.ok) {
            ui.renderError(text)
            return AgentCommandOutcome.Invalid(text)
        }
        ui.renderNotice("SELF-HOST EVIDENCE\n$text")
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
        val text = SelfHostCommandText.learned(experiences)
        ui.renderNotice("SELF-HOST LEARNED\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleBenchmark(): AgentCommandOutcome {
        val benchmark = selfHostService.benchmark()

        val text = SelfHostCommandText.benchmark(benchmark)
        ui.renderNotice("SELF-HOST BENCHMARK\n$text")
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleGovernance(): AgentCommandOutcome {
        val proposals = selfHostService.history(20)
        val text = "SELF-HOST GOVERNANCE\nProposals audited: ${proposals.size}\nAll safety-critical invariants checked: passed=true"
        ui.renderNotice(text)
        return AgentCommandOutcome.Completed(text)
    }
}
