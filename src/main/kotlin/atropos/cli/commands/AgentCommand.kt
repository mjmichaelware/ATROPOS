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
import atropos.core.provider.ProviderOnboardingService

sealed class AgentCommandOutcome {
    data class Completed(val text: String) : AgentCommandOutcome()

    /**
     * @param rendered whether the message has already reached the operator.
     *
     * Twenty-four sites in [SelfHostCommand] alone build an `Invalid` and
     * return it directly rather than through a helper that renders. Every one
     * of those was a command that produced a careful explanation and then
     * discarded it — `/self-host resume` printed nothing at all when there was
     * nothing to resume, which reads as a broken command rather than an
     * accurate answer.
     *
     * The flag exists rather than a rule ("always render at the boundary")
     * because some handlers legitimately render richer output themselves, and
     * a boundary that rendered unconditionally would print everything twice.
     * Default false means a new site is loud by default: forgetting to set it
     * shows the message, forgetting the old way hid it.
     */
    data class Invalid(val message: String, val rendered: Boolean = false) : AgentCommandOutcome()
}

fun interface AgentCommandHandler {
    fun execute(tokens: List<String>): AgentCommandOutcome
}

class AgentCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val activeProviderName: () -> String,
    private val providerOnboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val service: AgentService = AgentService(config, providerOnboarding = providerOnboarding),
    private val runService: AgentRunService = AgentRunService(config, onboarding = providerOnboarding),
    private val queueService: AgentQueueService = AgentQueueService(config, onboarding = providerOnboarding),
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
    private val importedInstructionPacks = ImportedInstructionPackStore(repoRoot)
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
    private val observationHandler = AgentObservationCommandHandler(ui, observer, journal, continuationService, invalid = ::invalid)
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
            "context" -> importContext(tokens.drop(2))
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
            "export" -> observationHandler.export(tokens.drop(2))
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

    private fun importContext(arguments: List<String>): AgentCommandOutcome {
        if (arguments.firstOrNull()?.lowercase() != "import" || arguments.size != 2) {
            return invalid("usage: /agent context import <cursor-rules-or-copilot-instructions-file>")
        }
        return importedInstructionPacks.import(arguments[1]).fold(
            onSuccess = { pack -> AgentCommandOutcome.Completed(
                "imported context pack ${pack.id} sha256=${pack.contentHash}; context-only (Source Authority and ATROPOS policy remain authoritative)"
            ) },
            onFailure = { failure -> invalid("context import refused: ${failure.message ?: "invalid instruction file"}") }
        )
    }

    private fun agentUsage(): String =
        "usage: /agent [status|run|enqueue|queue|daemon|jobs|job|ask|patch|apply|worker|verify|repair|session|runs|watch|tree|transcript|diff|tests|export|observe|dag|recover|worktree|gate|policy|goal|self-host]"

    private fun terminalWidth(): Int =
        System.getenv("COLUMNS")?.toIntOrNull()?.coerceAtLeast(40) ?: 80

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message, rendered = true)
    }

}
