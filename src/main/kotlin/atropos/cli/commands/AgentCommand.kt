package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AgentJobRenderer
import atropos.cli.ui.AgentQueueRenderer
import atropos.cli.ui.TerminalTheme
import atropos.cli.config.ConfigurationManager
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentDaemonService
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentService
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.ProviderSessionSupervisor
import atropos.core.agent.SupervisedSessionStore
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
    private val patchExtractor = AgentPatchExtractor()
    private val attestationRenderer = atropos.cli.ui.ContextAttestationRenderer(TerminalTheme(ConfigurationManager()))
    private val jobRenderer = AgentJobRenderer(TerminalTheme(ConfigurationManager()))
    private val queueRenderer = AgentQueueRenderer(TerminalTheme(ConfigurationManager()))
    private val patchDirectory = repoRoot.resolve(".atropos/agent/patches").normalize()
    private val jobSummaryMapper = AgentJobSummaryMapper(patchDirectory, patchExtractor)
    private val patchDisplay = AgentPatchDisplayHelper(patchExtractor)
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
    private val selfHostNaturalLanguageRouter = SelfHostNaturalLanguageRouter()

    private companion object { const val ATTESTATION_WIDTH = 80 }

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
                val runRequest = AgentCommandParser.parseRunRequest(tokens.drop(2))
                if (runRequest.task.isBlank()) {
                    return invalid("usage: /agent run [--smoke <command>] <task>")
                }

                ui.startSpinner("Planning durable agent job")
                return try {
                    val result = runService.run(activeProviderName(), runRequest.task, runRequest.smokeCommand)
                    lastKnownPatchId = result.appliedPatchId ?: result.patchId ?: lastKnownPatchId
                    val rendered = AgentCommandText.renderRendererOutput(
                        jobRenderer.renderRunSummary(jobSummaryMapper.toJobSummary(result), terminalWidth())
                    )
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent run failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "enqueue" -> {
                val request = AgentCommandParser.parseRunRequest(tokens.drop(2))
                if (request.task.isBlank()) {
                    return invalid("usage: /agent enqueue [--smoke <command>] <task>")
                }
                val record = queueService.enqueue(request.task, request.smokeCommand)
                val rendered = AgentCommandText.renderRendererOutput(
                    queueRenderer.renderDetail(record, terminalWidth())
                )
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "queue" -> {
                val result = queueHandler.execute(tokens.drop(2))
                lastKnownPatchId = result.lastKnownPatchId ?: lastKnownPatchId
                result.outcome
            }

            "daemon" -> daemonHandler.execute(tokens.drop(2))

            "status" -> {
                val snapshot = service.status(activeProviderName())
                lastKnownPatchId = snapshot.lastPatchId ?: lastKnownPatchId
                val rendered = AgentCommandText.formatBlock("AGENT STATUS", snapshot.render())
                ui.renderNotice(rendered)
                // Requirement 5: typed context failures must be explicit, not
                // only journaled. Surfaces the last recorded attestation failure.
                ui.renderNotice(
                    attestationRenderer.renderStatusRowsFromMemory(ATTESTATION_WIDTH)
                        .joinToString("\n")
                )
                AgentCommandOutcome.Completed(rendered)
            }

            "jobs" -> {
                val jobs = runService.listJobs()
                val rendered = AgentCommandText.renderRendererOutput(
                    jobRenderer.renderJobsList(jobs.map { jobSummaryMapper.toJobSummary(it) }, terminalWidth())
                )
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "job" -> {
                val jobRequest = AgentCommandParser.parseJobRequest(tokens.drop(2))
                val jobReference = jobRequest.reference
                if (jobReference == null) {
                    return invalid("usage: /agent job [<id|latest>] [--raw]")
                }

                val job = runService.resolveJob(jobReference)
                    ?: return invalid("job not found: $jobReference")
                val rendered = if (jobRequest.raw) {
                    AgentCommandText.formatBlock("AGENT JOB RAW", job.render())
                } else {
                    buildString {
                        append(
                            AgentCommandText.renderRendererOutput(
                                jobRenderer.renderJobDetail(
                                    jobSummaryMapper.toJobSummary(job),
                                    jobSummaryMapper.timelineEntries(job),
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
                val patchReference = AgentCommandParser.parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent verify [<patch-id|latest>]")
                }

                ui.startSpinner("Running deterministic verification")
                return try {
                    val result = service.verify(patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = AgentCommandText.formatBlock("AGENT VERIFY", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent verify failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "ask" -> {
                val task = tokens.drop(2).joinToString(" ").trim()
                if (task.isBlank()) {
                    return invalid("usage: /agent ask <task>")
                }

                // Short-input handler for ATROPOS identity queries
                val shortInputResult = identityResponder.respond(task)
                if (shortInputResult != null) {
                    val rendered = AgentCommandText.formatBlock("AGENT ASK", shortInputResult)
                    ui.renderNotice(rendered)
                    return AgentCommandOutcome.Completed(rendered)
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.ask(activeProviderName(), task)
                    val rendered = AgentCommandText.formatBlock("AGENT ASK", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent ask failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "repair" -> {
                val patchReference = AgentCommandParser.parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent repair [<patch-id|latest>]")
                }

                val preview = service.previewRepair(patchReference)
                if (preview != null) {
                    val rendered = AgentCommandText.formatBlock("AGENT REPAIR", preview.render())
                    ui.renderNotice(rendered)
                    return AgentCommandOutcome.Completed(rendered)
                }

                ui.startSpinner("Preparing repair patch")
                return try {
                    val result = service.repair(activeProviderName(), patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = AgentCommandText.formatBlock("AGENT REPAIR", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent repair failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "patch" -> {
                val patchRequest = AgentCommandParser.parsePatchRequest(tokens.drop(2))
                val task = patchRequest.task
                if (task.isBlank()) {
                    return invalid("usage: /agent patch [--provider <name>] <task>")
                }
                if (patchRequest.providerOverride != null && patchRequest.providerOverride !in patchProviderAllowList) {
                    return invalid("/agent patch provider override must be one of: ${patchProviderAllowList.joinToString(", ")}")
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.patch(activeProviderName(), task, patchRequest.providerOverride)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val body = buildString {
                        append(result.render())
                        patchDisplay.changedPathsPreview(result.patchPath)?.let {
                            appendLine()
                            append("Changed paths: $it")
                        }
                        appendLine()
                        append("Next command: ${patchDisplay.nextPatchCommand(result)}")
                    }
                    val rendered = AgentCommandText.formatBlock("AGENT PATCH", body)
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent patch failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "apply" -> {
                val applyRequest = AgentCommandParser.parseApplyRequest(tokens.drop(2))
                if (applyRequest.patchReference.isBlank()) {
                    return invalid("usage: /agent apply [--check|--verify] <patch-id|latest>")
                }
                if (applyRequest.checkOnly && applyRequest.verifyAfterApply) {
                    return invalid("/agent apply supports either --check or --verify, not both")
                }

                ui.startSpinner(
                    when {
                        applyRequest.checkOnly -> "Validating stored patch"
                        applyRequest.verifyAfterApply -> "Applying and verifying stored patch"
                        else -> "Applying stored patch"
                    }
                )
                return try {
                    val result = service.applyPatch(
                        applyRequest.patchReference,
                        applyRequest.checkOnly,
                        applyRequest.verifyAfterApply
                    )
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val body = buildString {
                        append(result.render())
                        if (result.applied) {
                            appendLine()
                            append("No commit created: changes are in the working tree only.")
                        }
                    }
                    val rendered = AgentCommandText.formatBlock(
                        when {
                            applyRequest.checkOnly -> "AGENT APPLY --CHECK"
                            applyRequest.verifyAfterApply -> "AGENT APPLY --VERIFY"
                            else -> "AGENT APPLY"
                        },
                        body
                    )
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent apply failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
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
        "usage: /agent [status|run|enqueue|queue|daemon|jobs|job|ask|patch|apply|verify|repair|session|runs|watch|tree|transcript|diff|tests|observe|dag|recover|worktree|gate|policy|goal|self-host]"

    private val patchProviderAllowList = setOf("github_models", "sambanova", "cloudflare_ai", "groq")

    private fun terminalWidth(): Int =
        System.getenv("COLUMNS")?.toIntOrNull()?.coerceAtLeast(40) ?: 80

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }

}
