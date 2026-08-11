package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AgentQueueRenderer
import atropos.cli.ui.TerminalTheme
import atropos.cli.config.ConfigurationManager
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentDaemonService
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentService
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.ProviderSessionSupervisor
import atropos.core.agent.SupervisedSessionStore
import atropos.core.agent.WorkerCodeProposalService
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagStore
import atropos.core.journal.EventJournalService
import atropos.core.observability.RunObserver
import atropos.core.policy.AutonomyPolicyEngine
import atropos.core.recovery.CrashRecoveryService
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.verification.VerifiedCompletionGate

sealed class AgentCommandOutcome {
    data class Completed(val text: String) : AgentCommandOutcome()
    data class Invalid(val message: String) : AgentCommandOutcome()
}

fun interface AgentCommandHandler {
    fun execute(tokens: List<String>): AgentCommandOutcome
}

class AgentCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val activeProviderName: () -> String,
    private val service: AgentService = AgentService(config),
    private val runService: AgentRunService = AgentRunService(config),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val daemonService: AgentDaemonService = AgentDaemonService(config),
    private val sessionSupervisor: ProviderSessionSupervisor = ProviderSessionSupervisor(),
    private val sessionStore: SupervisedSessionStore = SupervisedSessionStore(),
    private val continuationService: GoalContinuationService = GoalContinuationService(),
    private val dagService: DagExecutionService = DagExecutionService(config),
    private val dagStore: DagStore = DagStore(),
    private val journal: EventJournalService = EventJournalService(),
    private val observer: RunObserver = RunObserver(config),
    private val recoveryService: CrashRecoveryService = CrashRecoveryService(config),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(config),
    private val autonomyAdvisor: AutonomyPolicyEngine = AutonomyPolicyEngine()
) : AgentCommandHandler {
    private val repoRoot = AtroposRepoRootLocator.resolve()
    private val selfHostHandler: SelfHostCommand = SelfHostCommand(ui, config, repoRoot)
    private val queueRenderer = AgentQueueRenderer(TerminalTheme(ConfigurationManager()))
    private val daemonHandler = AgentDaemonCommandHandler(
        ui = ui,
        daemonService = daemonService,
        activeProviderName = activeProviderName,
        invalid = ::invalid
    )
    private val queueHandler = AgentQueueCommandHandler(
        ui = ui,
        queueService = queueService,
        queueRenderer = queueRenderer,
        activeProviderName = activeProviderName,
        terminalWidth = ::terminalWidth,
        currentPatchId = { lastKnownPatchId },
        invalid = ::invalid
    )
    private val sessionHandler = AgentSessionCommandHandler(ui, sessionSupervisor, ::invalid)
    private val goalHandler = AgentGoalCommandHandler(ui, continuationService, ::invalid)
    private val observationHandler = AgentObservationCommandHandler(ui, observer, journal, continuationService, ::invalid)
    private val recoveryHandler = AgentRecoveryCommandHandler(ui, recoveryService)
    private val dagHandler = AgentDagCommandHandler(ui, config, repoRoot, dagService, dagStore, ::invalid)
    private val worktreeHandler = AgentWorktreeCommandHandler(ui, worktreeService, ::invalid)
    private val gateHandler = AgentGateCommandHandler(ui, dagService, completionGate, ::invalid)
    private val policyHandler = AgentPolicyCommandHandler(ui, autonomyAdvisor, ::invalid)
    private val identityResponder = AgentIdentityResponder(
        repoRoot = repoRoot,
        service = service,
        continuationService = continuationService,
        sessionStore = sessionStore,
        activeProviderName = activeProviderName
    )
    private val jobHandler = AgentJobCommandHandler(
        ui = ui,
        repoRoot = repoRoot,
        service = service,
        runService = runService,
        queueService = queueService,
        activeProviderName = activeProviderName,
        terminalWidth = ::terminalWidth,
        currentPatchId = { lastKnownPatchId },
        invalid = ::invalid
    )
    private val patchHandler = AgentPatchCommandHandler(
        ui = ui,
        service = service,
        identityResponder = identityResponder,
        activeProviderName = activeProviderName,
        currentPatchId = { lastKnownPatchId },
        invalid = ::invalid
    )
    private val workerHandler = AgentWorkerCommandHandler(
        proposalService = WorkerCodeProposalService(service),
        activeProviderName = activeProviderName,
        invalid = ::invalid
    )
    private val selfHostNaturalLanguageRouter = SelfHostNaturalLanguageRouter()

    /** Last patch id ATROPOS has knowledge of, surfaced to the status line. Never implies a patch was applied. */
    var lastKnownPatchId: String? = null
        private set

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        selfHostNaturalLanguageRouter.route(tokens)?.let { routed ->
            return selfHostHandler.execute(routed)
        }
        if (tokens.size < 2) {
            return invalid(agentUsage())
        }

        return when (tokens[1].lowercase()) {
            "run" -> {
                val result = jobHandler.run(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "enqueue" -> {
                val result = jobHandler.enqueue(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "queue" -> {
                val result = queueHandler.execute(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "daemon" -> daemonHandler.execute(tokens.drop(2))

            "status" -> {
                val result = jobHandler.status()
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "jobs" -> {
                val result = jobHandler.jobs()
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "job" -> {
                val result = jobHandler.job(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "jobs" -> {
                val jobs = runService.listJobs()
                val rendered = renderRendererOutput(
                    jobRenderer.renderJobsList(jobs.map { it.toJobSummary() }, terminalWidth())
                )
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "job" -> {
                val jobRequest = parseJobRequest(tokens.drop(2))
                val jobReference = jobRequest.reference
                if (jobReference == null) {
                    return invalid("usage: /agent job [<id|latest>] [--raw]")
                }

                val job = runService.resolveJob(jobReference)
                    ?: return invalid("job not found: $jobReference")
                val rendered = if (jobRequest.raw) {
                    formatBlock("AGENT JOB RAW", job.render())
                } else {
                    buildString {
                        append(
                            renderRendererOutput(
                                jobRenderer.renderJobDetail(
                                    job.toJobSummary(),
                                    job.timelineEntries(),
                                    terminalWidth()
                                )
                            )
                        )
                        appendLine()
                        append("raw: /agent job ${job.id} --raw")
                    }.trimEnd()
                }
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "verify" -> {
                val result = jobHandler.verify(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "ask" -> {
                val result = patchHandler.ask(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "repair" -> {
                val result = patchHandler.repair(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "patch" -> {
                val result = patchHandler.patch(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "apply" -> {
                val result = patchHandler.apply(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "worker" -> {
                if (tokens.getOrNull(2)?.lowercase() != "propose") {
                    invalid("usage: /agent worker propose --worker <id> --territory <path[,path...]> [--provider <name>] <task>")
                } else {
                    workerHandler.propose(tokens.drop(3))
                }
            }

            "session" -> sessionHandler.execute(tokens.drop(2))
            "runs" -> observationHandler.runs()
            "watch" -> observationHandler.watch(tokens.drop(2))
            "tree" -> observationHandler.tree(tokens.drop(2))
            "transcript" -> observationHandler.transcript(tokens.drop(2))
            "diff" -> observationHandler.diff(tokens.drop(2))
            "tests" -> observationHandler.tests(tokens.drop(2))
            "observe" -> observationHandler.observe(tokens.drop(2))
            "dag" -> dagHandler.execute(tokens.drop(2))
            "recover" -> recoveryHandler.execute()
            "worktree" -> worktreeHandler.execute(tokens.drop(2))
            "gate" -> gateHandler.execute(tokens.drop(2))
            "policy" -> policyHandler.execute(tokens.drop(2))
            "goal" -> goalHandler.execute(tokens.drop(2))
            "self-host" -> selfHostHandler.execute(tokens)
            else -> invalid(agentUsage())
        }
    }

    private fun agentUsage(): String =
        "usage: /agent [status|run|enqueue|queue|daemon|jobs|job|ask|patch|apply|worker|verify|repair|session|runs|watch|tree|transcript|diff|tests|observe|dag|recover|worktree|gate|policy|goal|self-host]"

    private fun terminalWidth(): Int =
        System.getenv("COLUMNS")?.toIntOrNull()?.coerceAtLeast(40) ?: 80

    private fun parseRunRequest(args: List<String>): RunRequest {
        if (args.isEmpty()) return RunRequest(task = "")

        var smokeCommand: String? = null
        val taskParts = mutableListOf<String>()
        var index = 0

        while (index < args.size) {
            val token = args[index]
            when {
                token == "--smoke" -> {
                    val smoke = args.getOrNull(index + 1)?.trim()
                    if (smoke.isNullOrBlank() || smoke.startsWith("--")) {
                        return RunRequest()
                    }
                    smokeCommand = smoke
                    index += 2
                }
                token.startsWith("--smoke=") -> {
                    val smoke = token.substringAfter("=").trim()
                    if (smoke.isBlank()) return RunRequest()
                    smokeCommand = smoke
                    index++
                }
                token.startsWith("--") -> return RunRequest()
                else -> {
                    taskParts += token
                    index++
                }
            }
        }

        return RunRequest(
            smokeCommand = smokeCommand?.takeIf { it.isNotBlank() },
            task = taskParts.joinToString(" ").trim()
        )
    }

    private fun AgentJobRecord.toJobSummary(): AgentJobSummary =
        AgentJobSummary(
            id = id,
            task = task,
            status = toUiStatus(),
            provider = provider.takeIf { it.isNotBlank() },
            patchId = displayPatchId(),
            verificationId = verificationId?.takeIf { it.isNotBlank() },
            smokeCommand = smokeCommand?.takeIf { it.isNotBlank() },
            smokeSummary = smokeSummary(),
            finalReport = finalReport?.takeIf { it.isNotBlank() },
            commitProposal = commitProposal?.takeIf { it.isNotBlank() },
            nextSuggestedCommand = nextSuggestedCommand?.takeIf { it.isNotBlank() },
            contextExportPath = contextExportPath?.takeIf { it.isNotBlank() },
            startedAt = formatInstant(startedAt),
            updatedAt = formatInstant(updatedAt),
            changedPathsCount = changedPathsCount(),
            note = note()
        )

    private fun AgentJobRecord.timelineEntries(): List<AgentJobEvent> = buildList {
        addEvent(planAt, UiAgentJobStatus.PLANNING, null)
        addEvent(patchAt, UiAgentJobStatus.PATCHING, null)
        addEvent(applyAt, UiAgentJobStatus.APPLYING, applyNote())
        addEvent(verificationAt, UiAgentJobStatus.VERIFYING, verificationNote())
        addEvent(repairAt, UiAgentJobStatus.REPAIRING, repairNote())
        finishedAt?.let { finished ->
            add(
                AgentJobEvent(
                    at = formatInstant(finished),
                    status = toUiStatus(),
                    note = terminalNote()
                )
            )
        }
    }.distinctBy { it.at to it.status to it.note }

    private fun MutableList<AgentJobEvent>.addEvent(
        instant: java.time.Instant?,
        status: UiAgentJobStatus,
        note: String?
    ) {
        if (instant != null) {
            add(
                AgentJobEvent(
                    at = formatInstant(instant),
                    status = status,
                    note = note
                )
            )
        }
    }

    private fun AgentJobRecord.toUiStatus(): UiAgentJobStatus = when (status) {
        atropos.core.agent.AgentJobStatus.PLANNING -> UiAgentJobStatus.PLANNING
        atropos.core.agent.AgentJobStatus.PATCHING -> UiAgentJobStatus.PATCHING
        atropos.core.agent.AgentJobStatus.APPLYING -> UiAgentJobStatus.APPLYING
        atropos.core.agent.AgentJobStatus.REPAIRING -> UiAgentJobStatus.REPAIRING
        atropos.core.agent.AgentJobStatus.COMPLETED -> UiAgentJobStatus.PASSED
        atropos.core.agent.AgentJobStatus.FAILED -> if (looksRefused()) UiAgentJobStatus.REFUSED else UiAgentJobStatus.FAILED
        atropos.core.agent.AgentJobStatus.REFUSED -> UiAgentJobStatus.REFUSED
    }

    private fun AgentJobRecord.looksRefused(): Boolean {
        val text = listOfNotNull(failureReason, result, patchResult, applyResult, repairResult, smokeResult, finalReport)
            .joinToString(" ")
            .lowercase()
        return text.contains("refus") ||
            text.contains("unsafe") ||
            text.contains("forbidden") ||
            text.contains("no unified diff") ||
            text.contains("bad diff") ||
            text.contains("invalid patch")
    }

    private fun AgentJobRecord.displayPatchId(): String? =
        appliedPatchId?.takeIf { it.isNotBlank() }
            ?: patchId?.takeIf { it.isNotBlank() }

    private fun AgentJobRecord.changedPathsCount(): Int? {
        val patchId = displayPatchId() ?: return null
        val diffFile = patchDirectory.resolve("$patchId.diff").normalize()
        if (!diffFile.startsWith(patchDirectory) || !Files.isRegularFile(diffFile)) return null
        val diffText = runCatching { Files.readString(diffFile) }.getOrNull() ?: return null
        return patchExtractor.extract(diffText)?.touchedPaths?.size
    }

    private fun AgentJobRecord.note(): String? =
        when (status) {
            atropos.core.agent.AgentJobStatus.FAILED,
            atropos.core.agent.AgentJobStatus.REFUSED -> failureReason?.takeIf { it.isNotBlank() }
                ?: finalReport?.takeIf { it.isNotBlank() }
                ?: smokeSummary()
                ?: result
            else -> finalReport?.takeIf { it.isNotBlank() }
                ?: smokeSummary()
                ?: result
        }?.takeIf { it.isNotBlank() }

    private fun AgentJobRecord.terminalNote(): String? =
        when (toUiStatus()) {
            UiAgentJobStatus.PASSED -> finalReport?.takeIf { it.isNotBlank() } ?: result?.takeIf { it.isNotBlank() }
            UiAgentJobStatus.FAILED, UiAgentJobStatus.REFUSED -> failureReason?.takeIf { it.isNotBlank() } ?: finalReport?.takeIf { it.isNotBlank() } ?: result
            else -> null
        }?.takeIf { it.isNotBlank() }

    private fun AgentJobRecord.applyNote(): String? =
        applyResult?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotBlank() }

    private fun AgentJobRecord.verificationNote(): String? =
        verificationId?.takeIf { it.isNotBlank() }?.let { "verification $it" }

    private fun AgentJobRecord.repairNote(): String? =
        repairId?.takeIf { it.isNotBlank() }?.let { "repair $it" }

    private fun AgentJobRecord.smokeSummary(): String? {
        smokeResult?.takeIf { it.isNotBlank() }?.let { return it }
        smokeCommand?.takeIf { it.isNotBlank() }?.let { command ->
            val resultText = when {
                smokePassed == true -> "passed"
                smokePassed == false && smokeExitCode != null -> "failed exit ${smokeExitCode}"
                smokePassed == false -> "failed"
                else -> "not run"
            }
            val durationText = smokeDurationMillis?.let { "${it} ms" } ?: "unknown duration"
            return "$resultText · $command · $durationText"
        }
        return null
    }

    private fun formatInstant(instant: java.time.Instant?): String =
        instant?.let { timeFormatter.format(it) } ?: "unknown"

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }

}
