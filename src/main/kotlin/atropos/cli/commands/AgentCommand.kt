package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AgentJobEvent
import atropos.cli.ui.AgentJobRenderer
import atropos.cli.ui.AgentJobStatus as UiAgentJobStatus
import atropos.cli.ui.AgentJobSummary
import atropos.cli.ui.AgentQueueRenderer
import atropos.cli.ui.TerminalTheme
import atropos.cli.config.ConfigurationManager
import atropos.core.AtroposConfig
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentJobRecord
import atropos.core.agent.AgentDaemonDoctor
import atropos.core.agent.AgentDaemonService
import atropos.core.agent.AgentQueueDoctor
import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentService
import atropos.core.agent.AgentRunService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalTerminalCondition
import atropos.core.agent.ProviderSessionSupervisor
import atropos.core.agent.AgentRuntimeKind
import atropos.core.agent.SupervisedSessionState
import atropos.core.agent.SupervisedSessionStore
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.journal.EventJournalService
import atropos.core.observability.RunObserver
import atropos.core.policy.AutonomyActionClass
import atropos.core.policy.AutonomyPolicyEngine
import atropos.core.recovery.CrashRecoveryService
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.verification.VerifiedCompletionGate
import atropos.bootstrap.BootstrapAcceptanceDag
import atropos.cli.commands.SelfHostCommand
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Path
import java.nio.file.Files

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
    /** Advisory guidance only. Execution permission comes from BoundedAgencyGate. */
    private val autonomyAdvisor: AutonomyPolicyEngine = AutonomyPolicyEngine(),
    private val recoveryService: CrashRecoveryService = CrashRecoveryService(config),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(config)
) : AgentCommandHandler {
    private val repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val selfHostHandler: SelfHostCommand = SelfHostCommand(ui, config, repoRoot)
    private val patchExtractor = AgentPatchExtractor()
    private val attestationRenderer = atropos.cli.ui.ContextAttestationRenderer(TerminalTheme(ConfigurationManager()))
    private val jobRenderer = AgentJobRenderer(TerminalTheme(ConfigurationManager()))
    private val queueRenderer = AgentQueueRenderer(TerminalTheme(ConfigurationManager()))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val patchDirectory = repoRoot.resolve(".atropos/agent/patches").normalize()

    private companion object { const val ATTESTATION_WIDTH = 80 }

    /** Last patch id ATROPOS has knowledge of, surfaced to the status line. Never implies a patch was applied. */
    var lastKnownPatchId: String? = null
        private set

    /**
     * Handle short input queries about ATROPOS identity and state.
     *
     * Returns a rendered status string if the input is a short query about
     * ATROPOS, or null to pass through to normal provider dispatch.
     */
    private fun handleAtroposShortInput(task: String): String? {
        val lower = task.trim().lowercase()

        // Explicit Greek mythology request — allow through to provider
        if ((lower.contains("greek") || lower.contains("mythology") || lower.contains("myth")) &&
            (lower.contains("atropos") || lower.contains("fate") || lower.contains("moirai"))
        ) {
            return null
        }

        // "ATROPOS" alone — report current state
        if (lower == "atropos" || lower == "what is atropos" || lower == "what is atropos doing?" ||
            lower == "what is atropos doing" || lower == "who is atropos" || lower == "who are you" ||
            lower == "what are you" || lower == "tell me about yourself"
        ) {
            val snapshot = service.status(activeProviderName())
            val goals = continuationService.listRuns(Int.MAX_VALUE).runs
            val selfHostGoals = goals.filter { it.provider == "self-host" }
            val sessions = sessionStore.listSessions()
            val activeSessions = sessions.count { it.state == SupervisedSessionState.IDLE || it.state == SupervisedSessionState.BUSY }

            return buildString {
                appendLine("ATROPOS runtime state")
                appendLine()
                appendLine("Repository: ${repoRoot.fileName}")
                appendLine("Repository root: $repoRoot")
                appendLine("Active provider: ${snapshot.activeProvider}")
                appendLine("Provider order: ${snapshot.providerOrder.joinToString(" -> ").ifBlank { "none" }}")
                appendLine("Patch provider order: ${snapshot.patchProviderOrder.joinToString(" -> ").ifBlank { "none" }}")
                appendLine("Last patch: ${snapshot.lastPatchId ?: "none"}")
                appendLine("Owns repo read/write: ${if (snapshot.ownsRepoReadWrite) "yes" else "no"}")
                appendLine("Self-host goals: ${selfHostGoals.size}")
                selfHostGoals.firstOrNull()?.let { goal ->
                    appendLine("Self-host current: ${goal.id} ${goal.status} phase=${goal.activePhase ?: "none"} node=${goal.currentNodeId ?: "none"}")
                }
                appendLine()
                if (goals.isNotEmpty()) {
                    appendLine("Recent goal runs: ${goals.size}")
                    goals.take(3).forEach { goal: atropos.core.agent.GoalRunRecord ->
                        appendLine("  ${goal.id}: ${goal.status} (phase ${goal.activePhase ?: "?"})")
                    }
                }
                if (activeSessions > 0) {
                    appendLine("Active provider sessions: $activeSessions")
                }
                appendLine()
                appendLine("Type /help to see available commands.")
                appendLine("Type /agent status for detailed agent state.")
                appendLine("Type /status route for provider routing.")
            }.trimEnd()
        }

        // "Fix ATROPOS" — remain in repository context
        if (lower.startsWith("fix atropos")) {
            return buildString {
                appendLine("ATROPOS is the current repository and autonomous software engine.")
                appendLine()
                appendLine("To fix something specific, describe the task. For example:")
                appendLine("  /agent ask refactor the prompt builder in AgentPromptContract.kt")
                appendLine("  /agent patch add null check to AgentService.ask()")
                appendLine("  /agent run --smoke './gradlew compileKotlin' fix the compile error")
                appendLine()
                appendLine("All work remains inside this repository at:")
                appendLine("  $repoRoot")
            }.trimEnd()
        }

        // Not a short input — pass through
        return null
    }

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        if (tokens.size < 2) {
            return invalid(agentUsage())
        }

        return when (tokens[1].lowercase()) {
            "run" -> {
                val runRequest = parseRunRequest(tokens.drop(2))
                if (runRequest.task.isBlank()) {
                    return invalid("usage: /agent run [--smoke <command>] <task>")
                }

                ui.startSpinner("Planning durable agent job")
                return try {
                    val result = runService.run(activeProviderName(), runRequest.task, runRequest.smokeCommand)
                    lastKnownPatchId = result.appliedPatchId ?: result.patchId ?: lastKnownPatchId
                    val rendered = renderRendererOutput(
                        jobRenderer.renderRunSummary(result.toJobSummary(), terminalWidth())
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
                val request = parseRunRequest(tokens.drop(2))
                if (request.task.isBlank()) {
                    return invalid("usage: /agent enqueue [--smoke <command>] <task>")
                }
                val record = queueService.enqueue(request.task, request.smokeCommand)
                val rendered = renderRendererOutput(
                    queueRenderer.renderDetail(record, terminalWidth())
                )
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "queue" -> handleQueueCommand(tokens.drop(2))

            "daemon" -> handleDaemonCommand(tokens.drop(2))

            "status" -> {
                val snapshot = service.status(activeProviderName())
                lastKnownPatchId = snapshot.lastPatchId ?: lastKnownPatchId
                val rendered = formatBlock("AGENT STATUS", snapshot.render())
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
                val patchReference = parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent verify [<patch-id|latest>]")
                }

                ui.startSpinner("Running deterministic verification")
                return try {
                    val result = service.verify(patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = formatBlock("AGENT VERIFY", result.render())
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
                val shortInputResult = handleAtroposShortInput(task)
                if (shortInputResult != null) {
                    val rendered = formatBlock("AGENT ASK", shortInputResult)
                    ui.renderNotice(rendered)
                    return AgentCommandOutcome.Completed(rendered)
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.ask(activeProviderName(), task)
                    val rendered = formatBlock("AGENT ASK", result.render())
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
                val patchReference = parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent repair [<patch-id|latest>]")
                }

                val preview = service.previewRepair(patchReference)
                if (preview != null) {
                    val rendered = formatBlock("AGENT REPAIR", preview.render())
                    ui.renderNotice(rendered)
                    return AgentCommandOutcome.Completed(rendered)
                }

                ui.startSpinner("Preparing repair patch")
                return try {
                    val result = service.repair(activeProviderName(), patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = formatBlock("AGENT REPAIR", result.render())
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
                val patchRequest = parsePatchRequest(tokens.drop(2))
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
                        changedPathsPreview(result.patchPath)?.let {
                            appendLine()
                            append("Changed paths: $it")
                        }
                        appendLine()
                        append("Next command: ${nextPatchCommand(result)}")
                    }
                    val rendered = formatBlock("AGENT PATCH", body)
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
                val applyRequest = parseApplyRequest(tokens.drop(2))
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
                    val rendered = formatBlock(
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

            "session" -> handleSessionCommand(tokens.drop(2))
            "runs" -> handleRunsCommand(tokens.drop(2))
            "watch" -> handleWatchCommand(tokens.drop(2))
            "tree" -> handleTreeCommand(tokens.drop(2))
            "transcript" -> handleTranscriptCommand(tokens.drop(2))
            "diff" -> handleAgentDiffCommand(tokens.drop(2))
            "tests" -> handleAgentTestsCommand(tokens.drop(2))
            "observe" -> handleObserveCommand(tokens.drop(2))
            "dag" -> handleDagCommand(tokens.drop(2))
            "recover" -> handleRecoverCommand(tokens.drop(2))
            "worktree" -> handleWorktreeCommand(tokens.drop(2))
            "gate" -> handleGateCommand(tokens.drop(2))
            "goal" -> handleGoalCommand(tokens.drop(2))
            "self-host" -> selfHostHandler.execute(tokens)
            else -> invalid(agentUsage())
        }
    }

    private fun agentUsage(): String =
        "usage: /agent [status|run|enqueue|queue|daemon|jobs|job|ask|patch|apply|verify|repair|session|runs|watch|tree|transcript|diff|tests|observe|dag|recover|worktree|gate|goal|self-host]"

    private fun handleDaemonCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            "once" -> {
                ui.startSpinner("Running daemon once")
                try {
                    val result = daemonService.once(activeProviderName())
                    val rendered = formatBlock("AGENT DAEMON ONCE", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } finally {
                    ui.stopSpinner()
                }
            }
            "foreground" -> {
                val result = daemonService.foreground(activeProviderName())
                val rendered = formatBlock("AGENT DAEMON FOREGROUND", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "start" -> {
                val result = daemonService.start()
                val rendered = formatBlock("AGENT DAEMON START", result.render())
                if (result.ok) ui.renderNotice(rendered) else ui.renderError(rendered)
                if (result.ok) AgentCommandOutcome.Completed(rendered) else AgentCommandOutcome.Invalid(rendered)
            }
            "stop" -> {
                val result = daemonService.stop()
                val rendered = formatBlock("AGENT DAEMON STOP", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            null, "status" -> {
                val result = daemonService.status()
                val rendered = formatBlock("AGENT DAEMON STATUS", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "doctor" -> {
                val result = AgentDaemonDoctor().run()
                val rendered = formatBlock("AGENT DAEMON DOCTOR", result.render())
                if (result.passed) {
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } else {
                    ui.renderError(rendered)
                    AgentCommandOutcome.Invalid(rendered)
                }
            }
            else -> invalid("usage: /agent daemon [once|foreground|start|stop|status|doctor]")
        }
    }

    private fun handleQueueCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null -> {
                val rendered = renderRendererOutput(queueRenderer.renderList(queueService.list(), terminalWidth()))
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "show" -> {
                val request = parseQueueShowRequest(args.drop(1))
                val reference = request.reference ?: return invalid("usage: /agent queue show [<queue-id|latest>] [--raw]")
                val record = queueService.resolve(reference) ?: return invalid("queue entry not found: $reference")
                val rendered = if (request.raw) {
                    formatBlock("AGENT QUEUE RAW", record.renderRaw())
                } else {
                    buildString {
                        append(renderRendererOutput(queueRenderer.renderDetail(record, terminalWidth())))
                        appendLine()
                        append("raw: /agent queue show ${record.id} --raw")
                    }.trimEnd()
                }
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "run" -> handleQueueRun(args.drop(1))
            "resume" -> {
                val reference = parseReference(args.drop(1)) ?: return invalid("usage: /agent queue resume [<queue-id|latest>]")
                ui.startSpinner("Resuming queued agent work")
                return try {
                    val result = queueService.resume(activeProviderName(), reference)
                    result.jobRecord?.let { lastKnownPatchId = it.appliedPatchId ?: it.patchId ?: lastKnownPatchId }
                    val rendered = renderQueueRunResult("AGENT QUEUE RESUME", result)
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } finally {
                    ui.stopSpinner()
                }
            }
            "cancel" -> {
                val reference = parseReference(args.drop(1)) ?: return invalid("usage: /agent queue cancel [<queue-id|latest>]")
                val record = queueService.cancel(reference)
                    ?: return invalid("queue entry not found: $reference")
                val rendered = renderRendererOutput(queueRenderer.renderDetail(record, terminalWidth()))
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "recover" -> {
                val result = queueService.recover()
                val rendered = formatBlock("AGENT QUEUE RECOVER", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "doctor" -> {
                val result = AgentQueueDoctor().run()
                val rendered = formatBlock("AGENT QUEUE DOCTOR", result.render())
                if (result.passed) {
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } else {
                    ui.renderError(rendered)
                    AgentCommandOutcome.Invalid(rendered)
                }
            }
            else -> invalid("usage: /agent queue [show <queue-id|latest> [--raw]|run next|run --max <count>|resume <queue-id|latest>|cancel <queue-id|latest>|recover|doctor]")
        }
    }

    private fun handleQueueRun(args: List<String>): AgentCommandOutcome {
        return when {
            args.size == 1 && args[0].equals("next", ignoreCase = true) -> {
                ui.startSpinner("Running next queued agent job")
                try {
                    val result = queueService.runNext(activeProviderName())
                    result.jobRecord?.let { lastKnownPatchId = it.appliedPatchId ?: it.patchId ?: lastKnownPatchId }
                    val rendered = renderQueueRunResult("AGENT QUEUE RUN", result)
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } finally {
                    ui.stopSpinner()
                }
            }
            args.size == 2 && args[0] == "--max" -> {
                val max = args[1].toIntOrNull()
                    ?: return invalid("usage: /agent queue run --max <1-${atropos.core.agent.AgentQueueDefaults.MAX_RUN_COUNT}>")
                ui.startSpinner("Running queued agent batch")
                try {
                    val result = queueService.runMax(activeProviderName(), max)
                    result.results.mapNotNull { it.jobRecord }.lastOrNull()?.let {
                        lastKnownPatchId = it.appliedPatchId ?: it.patchId ?: lastKnownPatchId
                    }
                    val rendered = formatBlock("AGENT QUEUE RUN", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } finally {
                    ui.stopSpinner()
                }
            }
            else -> invalid("usage: /agent queue run next | /agent queue run --max <count>")
        }
    }

    private fun renderQueueRunResult(title: String, result: atropos.core.agent.AgentQueueRunResult): String = buildString {
        appendLine("── $title ──")
        appendLine(result.message)
        val record = result.queueRecord
        if (record != null) {
            queueRenderer.renderDetail(record, terminalWidth()).forEach { appendLine(it) }
        }
        val job = result.jobRecord
        if (job != null) {
            appendLine()
            appendLine("job: ${job.id}")
            appendLine("provider: ${job.provider}")
            appendLine("patch: ${job.appliedPatchId ?: job.patchId ?: "none"}")
            appendLine("verification: ${job.verificationId ?: "none"}")
            appendLine("smoke: ${job.smokeResult ?: "none"}")
        }
    }.trimEnd()

    private fun changedPathsPreview(patchPath: java.nio.file.Path?, limit: Int = 6): String? {
        if (patchPath == null || !Files.isRegularFile(patchPath)) return null
        val diffText = runCatching { Files.readString(patchPath) }.getOrNull() ?: return null
        val paths = patchExtractor.extract(diffText)?.touchedPaths ?: return null
        if (paths.isEmpty()) return null
        val shown = paths.take(limit).joinToString(", ")
        val remaining = paths.size - limit
        return if (remaining > 0) "$shown (+$remaining more)" else shown
    }

    private fun nextPatchCommand(result: atropos.core.agent.AgentPatchRunResult): String = when {
        result.patchId == null -> "/agent patch <task>"
        result.checkResult == null -> "/agent apply --check ${result.patchId}"
        result.checkResult.passed -> "/agent apply --check ${result.patchId}  (check already OK)"
        else -> "/agent patch <task>  (git apply --check failed, regenerate)"
    }

    private fun formatBlock(title: String, body: String): String = buildString {
        appendLine("── $title ──")
        body.lineSequence().forEach { line -> append(wrapLine(line)).append('\n') }
    }.trimEnd()

    private fun renderRendererOutput(lines: List<String>): String =
        lines.joinToString("\n").trimEnd()

    // Only very long unbroken lines are pre-wrapped; the reactive renderer already
    // wraps every transcript line at the live terminal width, so wrapping shorter
    // lines here too would double-wrap and mangle the output.
    private fun wrapLine(line: String, width: Int = 320): String {
        if (line.length <= width) return line
        val leading = line.takeWhile { it == ' ' }
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return line

        val available = (width - leading.length).coerceAtLeast(10)
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > available) {
                segments += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) segments += current.toString()

        return leading + segments.joinToString("\n$leading  ")
    }

    private data class PatchRequest(
        val providerOverride: String? = null,
        val task: String = ""
    )

    private data class ApplyRequest(
        val patchReference: String = "",
        val checkOnly: Boolean = false,
        val verifyAfterApply: Boolean = false
    )

    private data class JobRequest(
        val reference: String? = null,
        val raw: Boolean = false
    )

    private val patchProviderAllowList = setOf("github_models", "sambanova", "cloudflare_ai", "groq")

    private fun terminalWidth(): Int =
        System.getenv("COLUMNS")?.toIntOrNull()?.coerceAtLeast(40) ?: 80

    private fun parsePatchRequest(args: List<String>): PatchRequest {
        if (args.isEmpty()) return PatchRequest(task = "")

        var index = 0
        var providerOverride: String? = null

        while (index < args.size) {
            val token = args[index]
            when {
                token == "--provider" -> {
                    if (index + 1 >= args.size) {
                        return PatchRequest(task = "")
                    }
                    providerOverride = args[index + 1].trim().lowercase()
                    index += 2
                }
                token.startsWith("--provider=") -> {
                    providerOverride = token.substringAfter("=").trim().lowercase()
                    index++
                }
                token.startsWith("--") -> {
                    break
                }
                else -> break
            }
        }

        val task = args.drop(index).joinToString(" ").trim()
        return PatchRequest(providerOverride = providerOverride?.takeIf { it.isNotBlank() }, task = task)
    }

    private fun parseReference(args: List<String>): String? {
        if (args.isEmpty()) return "latest"
        if (args.size == 1 && !args[0].startsWith("--")) return args[0].trim().takeIf { it.isNotBlank() }
        return null
    }

    private fun parseJobRequest(args: List<String>): JobRequest {
        if (args.isEmpty()) return JobRequest(reference = "latest")

        var raw = false
        val referenceParts = mutableListOf<String>()

        for (token in args) {
            when {
                token == "--raw" || token.equals("raw", ignoreCase = true) -> raw = true
                token.startsWith("--raw=") -> raw = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--") -> return JobRequest()
                else -> referenceParts += token.trim()
            }
        }

        val reference = referenceParts.joinToString(" ").trim().ifBlank { "latest" }
        return JobRequest(reference = reference, raw = raw)
    }

    private fun parseQueueShowRequest(args: List<String>): JobRequest =
        parseJobRequest(args)

    private fun parseApplyRequest(args: List<String>): ApplyRequest {
        if (args.isEmpty()) return ApplyRequest(patchReference = "latest")

        var checkOnly = false
        var verifyAfterApply = false
        var patchReference: String? = null

        for (token in args) {
            when {
                token == "--check" -> checkOnly = true
                token == "--verify" -> verifyAfterApply = true
                token.startsWith("--check=") -> checkOnly = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--verify=") -> verifyAfterApply = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--") -> return ApplyRequest()
                patchReference == null -> patchReference = token.trim()
                else -> return ApplyRequest()
            }
        }

        return ApplyRequest(
            patchReference = patchReference?.takeIf { it.isNotBlank() } ?: "latest",
            checkOnly = checkOnly,
            verifyAfterApply = verifyAfterApply
        )
    }

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

    // --- Session (M1) ---
    private fun handleSessionCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "status" -> {
                val text = sessionSupervisor.status()
                ui.renderNotice(formatBlock("SESSIONS", text))
                AgentCommandOutcome.Completed(text)
            }
            "create" -> {
                val result = sessionSupervisor.createSession(AgentRuntimeKind.OPENCODE, args.getOrNull(1)?.toIntOrNull())
                ui.renderNotice(formatBlock("SESSION CREATE", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "connect" -> {
                val sid = args.getOrNull(1) ?: return invalid("usage: /agent session connect <session-id> <provider-session-id>")
                val psid = args.getOrNull(2) ?: return invalid("usage: /agent session connect <session-id> <provider-session-id>")
                val result = sessionSupervisor.connectSession(sid, psid)
                ui.renderNotice(formatBlock("SESSION CONNECT", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "mark" -> {
                val sid = args.getOrNull(1) ?: return invalid("usage: /agent session mark <session-id> <state> [reason]")
                val state = args.getOrNull(2)?.lowercase() ?: return invalid("usage: /agent session mark <session-id> <state>")
                val reason = args.drop(3).joinToString(" ").ifBlank { "manual mark" }
                val result = when (state) {
                    "idle" -> sessionSupervisor.markBusy(sid)
                    "busy" -> sessionSupervisor.markBusy(sid)
                    "failed" -> sessionSupervisor.markFailed(sid, reason)
                    "complete" -> sessionSupervisor.markComplete(sid)
                    "unavailable" -> sessionSupervisor.markUnavailable(sid, reason)
                    else -> return invalid("invalid state: $state (idle/busy/failed/complete/unavailable)")
                }
                ui.renderNotice(formatBlock("SESSION MARK", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "heartbeat" -> {
                val sid = args.getOrNull(1) ?: return invalid("usage: /agent session heartbeat <session-id>")
                val result = sessionSupervisor.heartbeat(sid)
                ui.renderNotice(formatBlock("SESSION HEARTBEAT", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "show" -> {
                val sid = args.getOrNull(1) ?: return invalid("usage: /agent session show <session-id>")
                val record = sessionSupervisor.readSession(sid)
                if (record == null) return invalid("session not found: $sid")
                ui.renderNotice(formatBlock("SESSION", record.render()))
                AgentCommandOutcome.Completed(record.render())
            }
            else -> invalid("usage: /agent session [status|create|connect|mark|heartbeat|show]")
        }
    }

    // --- Goal Runs (M2) ---
    private fun handleGoalCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "list" -> {
                val runs = continuationService.listRuns()
                ui.renderNotice(formatBlock("GOAL RUNS", runs.message + "\n" + runs.runs.joinToString("\n") { it.renderSummaryLine() }))
                AgentCommandOutcome.Completed(runs.message)
            }
            "start" -> {
                val task = args.drop(1).joinToString(" ").ifBlank { return invalid("usage: /agent goal start <task>") }
                val run = continuationService.startRun(task)
                ui.renderNotice(formatBlock("GOAL START", "run: ${run.id}"))
                AgentCommandOutcome.Completed("started: ${run.id}")
            }
            "complete" -> {
                val rid = args.getOrNull(1) ?: return invalid("usage: /agent goal complete <run-id> <condition>")
                val cond = args.getOrNull(2)?.let { runCatching { GoalTerminalCondition.valueOf(it.uppercase()) }.getOrNull() }
                    ?: GoalTerminalCondition.VERIFIED_COMPLETE
                val result = continuationService.completeRun(rid, cond)
                ui.renderNotice(formatBlock("GOAL COMPLETE", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "show" -> {
                val rid = args.getOrNull(1) ?: return invalid("usage: /agent goal show <run-id>")
                val run = continuationService.resolveRun(rid) ?: return invalid("run not found: $rid")
                ui.renderNotice(formatBlock("GOAL RUN", run.render()))
                AgentCommandOutcome.Completed(run.render())
            }
            else -> invalid("usage: /agent goal [list|start|complete|show]")
        }
    }

    // --- Observability (M6) ---
    private fun handleRunsCommand(args: List<String>): AgentCommandOutcome {
        val text = observer.listRuns()
        ui.renderNotice(formatBlock("RUNS", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleWatchCommand(args: List<String>): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref)
        if (runId == null) return invalid("no runs to watch")
        val events = journal.readEvents(runId, 20)
        val text = events.joinToString("\n") { it.render() }
        ui.renderNotice(formatBlock("WATCH $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleTreeCommand(args: List<String>): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref)
        if (runId == null) return invalid("no runs")
        val text = observer.tree(runId)
        ui.renderNotice(formatBlock("TREE $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleTranscriptCommand(args: List<String>): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref)
        if (runId == null) return invalid("no runs")
        val text = observer.transcript(runId)
        ui.renderNotice(formatBlock("TRANSCRIPT $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleAgentDiffCommand(args: List<String>): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref)
        if (runId == null) return invalid("no runs")
        val text = observer.diffLog(runId)
        ui.renderNotice(formatBlock("DIFF $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun handleAgentTestsCommand(args: List<String>): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref)
        if (runId == null) return invalid("no runs")
        val text = observer.testLog(runId)
        ui.renderNotice(formatBlock("TESTS $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun resolveObservedRunId(reference: String): String? {
        if (!reference.equals("latest", ignoreCase = true)) return reference
        return journal.latestRunId() ?: continuationService.latestRun()?.id
    }

    private fun handleObserveCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "status" -> {
                val state = observer.status()
                val text = buildString {
                    append("port=${state.dashboardPort} running=${state.running} clients=${state.connectedClients}")
                    state.lastError?.let { append(" lastError=$it") }
                }
                ui.renderNotice(formatBlock("OBSERVER", text))
                AgentCommandOutcome.Completed("observer status: $text")
            }
            "start" -> {
                val msg = observer.start(args.getOrNull(1)?.toIntOrNull() ?: 4197)
                ui.renderNotice(formatBlock("OBSERVER START", msg))
                AgentCommandOutcome.Completed(msg)
            }
            "stop" -> {
                val msg = observer.stop()
                ui.renderNotice(formatBlock("OBSERVER STOP", msg))
                AgentCommandOutcome.Completed(msg)
            }
            "open" -> {
                val state = observer.status()
                if (!state.running) {
                    val msg = buildString {
                        append("observer not running")
                        state.lastError?.let { append(": $it") }
                    }
                    ui.renderError(msg)
                    return AgentCommandOutcome.Invalid(msg)
                }
                val msg = "dashboard: http://127.0.0.1:${state.dashboardPort}"
                ui.renderNotice(formatBlock("OBSERVER OPEN", msg))
                AgentCommandOutcome.Completed(msg)
            }
            else -> invalid("usage: /agent observe [status|start|stop|open]")
        }
    }

    // --- DAG (M4) ---
    private fun handleDagCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "list" -> {
                val dags = dagService.listDags()
                val text = dags.joinToString("\n") { "${it.id}: ${it.label} (${it.nodes.size} nodes)" }.ifEmpty { "no DAGs" }
                ui.renderNotice(formatBlock("DAGS", text))
                AgentCommandOutcome.Completed(text)
            }
            "create" -> {
                val label = args.getOrNull(1) ?: return invalid("usage: /agent dag create <label> [--node <id>,<dep1,dep2>,<action>]...")
                val name = label
                val nodes = mutableListOf<DagNode>()
                var idx = 2
                while (idx < args.size) {
                    when (args[idx]) {
                        "--node" -> {
                            val parts = args.getOrNull(idx + 1)?.split(",", limit = 3) ?: return invalid("invalid node spec")
                            val nodeId = parts.getOrElse(0) { "n${nodes.size + 1}" }
                            val deps = parts.getOrElse(1) { "" }.split("+").filter { it.isNotBlank() }
                            val action = runCatching { DagNodeAction.valueOf(parts.getOrElse(2) { "RUN_COMMAND" }.uppercase()) }.getOrNull() ?: DagNodeAction.RUN_COMMAND
                            val now = java.time.Instant.now()
                            nodes.add(
                                DagNode(
                                    id = nodeId, label = nodeId, dependencies = deps, action = action,
                                    actionPayload = null, createdAt = now, updatedAt = now,
                                    metaFile = dagStore.dagDir().resolve("$nodeId.meta")
                                )
                            )
                            idx += 2
                        }
                        else -> break
                    }
                }
                if (nodes.isEmpty()) return invalid("at least one --node required")
                val dag = dagService.createDag(name, nodes)
                ui.renderNotice(formatBlock("DAG CREATE", "${dag.id} with ${dag.nodes.size} nodes"))
                AgentCommandOutcome.Completed("created: ${dag.id}")
            }
            "run" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag run <dag-id>")
                val result = dagService.evaluateDag(dagId)
                ui.renderNotice(formatBlock("DAG RUN", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "show" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag show <dag-id>")
                val dag = dagService.readDag(dagId) ?: return invalid("DAG not found: $dagId")
                ui.renderNotice(formatBlock("DAG", dag.render()))
                AgentCommandOutcome.Completed(dag.render())
            }
            "status" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag status <dag-id>")
                val status = dagService.status(dagId) ?: return invalid("DAG not found: $dagId")
                ui.renderNotice(formatBlock("DAG STATUS", "${status.message}\ncompleted=${status.completedNodes} failed=${status.failedNodes} blocked=${status.blockedNodes} pending=${status.pendingNodes} running=${status.runningNodes} ready=${status.readyNodes}"))
                AgentCommandOutcome.Completed(status.message)
            }
            "recover" -> {
                val count = dagService.recoverStaleClaims()
                ui.renderNotice(formatBlock("DAG RECOVER", "recovered $count stale claims"))
                AgentCommandOutcome.Completed("recovered $count stale claims")
            }
            "node" -> {
                val nodeId = args.getOrNull(1) ?: return invalid("usage: /agent dag node <node-id>")
                val node = dagService.readNode(nodeId) ?: return invalid("node not found: $nodeId")
                ui.renderNotice(formatBlock("DAG NODE", node.render()))
                AgentCommandOutcome.Completed(node.render())
            }
            "delete" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag delete <dag-id>")
                dagStore.deleteDag(dagId)
                ui.renderNotice(formatBlock("DAG DELETE", "deleted: $dagId"))
                AgentCommandOutcome.Completed("deleted: $dagId")
            }
            "bootstrap" -> {
                ui.startSpinner("Running bootstrap acceptance DAG")
                return try {
                    val acceptanceDag = BootstrapAcceptanceDag(config, repoRoot)
                    val result = acceptanceDag.createAndRun()
                    val text = buildString {
                        appendLine("Bootstrap acceptance: ${if (result.passed) "PASSED" else "FAILED"}")
                        appendLine("nodes attempted: ${result.nodesAttempted}")
                        appendLine("nodes passed: ${result.nodesPassed}")
                        appendLine("nodes failed: ${result.nodesFailed}")
                        if (result.details.isNotEmpty()) {
                            appendLine()
                            appendLine("details:")
                            result.details.forEach { appendLine("  $it") }
                        }
                    }.trimEnd()
                    ui.renderNotice(formatBlock("BOOTSTRAP DAG", text))
                    if (result.passed) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
                } catch (e: Exception) {
                    val message = "bootstrap DAG failed: ${e.message}"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }
            else -> invalid("usage: /agent dag [list|create|run|show|status|recover|node|delete|bootstrap]")
        }
    }

    // --- Recovery (M7) ---
    private fun handleRecoverCommand(args: List<String>): AgentCommandOutcome {
        val report = recoveryService.recover()
        val text = recoveryService.renderReport(report)
        ui.renderNotice(formatBlock("RECOVERY", text))
        return if (report.errors.isEmpty()) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
    }

    // --- Policy (M3) ---
    private fun handlePolicyCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "audit" -> {
                val audit = autonomyAdvisor.latestAudit()
                val text = audit.joinToString("\n") { "${it.decidedAt} ${it.actionClass} advisory_allowed=${it.advisoryAllowed} advisory_blocked=${it.advisoryBlocked} ${it.reason}" }.ifEmpty { "no audit records" }
                ui.renderNotice(formatBlock("AUTONOMY ADVISORY AUDIT", text))
                AgentCommandOutcome.Completed(text)
            }
            "check" -> {
                val action = args.getOrNull(1)?.let { runCatching { AutonomyActionClass.valueOf(it.uppercase()) }.getOrNull() }
                    ?: return invalid("usage: /agent policy check <ActionClass>")
                val desc = args.drop(2).joinToString(" ")
                // Advisory only. This reports guidance; it authorises nothing.
                // Execution permission comes from BoundedAgencyGate alone.
                val decision = autonomyAdvisor.advise(action, mapOf("description" to desc))
                val text = "action=$action advisory_allowed=${decision.advisoryAllowed} " +
                    "advisory_blocked=${decision.advisoryBlocked} reason=${decision.reason} " +
                    "(advisory only — not an execution permit)"
                ui.renderNotice(formatBlock("AUTONOMY ADVICE", text))
                if (decision.advisoryAllowed) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            else -> invalid("usage: /agent policy [audit|check]")
        }
    }

    // --- Worktree (M8) ---
    private fun handleWorktreeCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "list" -> {
                val wts = worktreeService.listWorktrees()
                val text = wts.joinToString("\n") { "${it.id}: job=${it.jobId} verified=${it.verified} rolledBack=${it.rolledBack} merged=${it.mergedBack}" }.ifEmpty { "no worktrees" }
                ui.renderNotice(formatBlock("WORKTREES", text))
                AgentCommandOutcome.Completed(text)
            }
            "create" -> {
                val jobId = args.getOrNull(1) ?: return invalid("usage: /agent worktree create <job-id> [--territory path,...]")
                val territoryIdx = args.indexOf("--territory")
                val territory = if (territoryIdx >= 0) args.getOrNull(territoryIdx + 1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList() else emptyList()
                val result = worktreeService.createWorktree(jobId, territory)
                ui.renderNotice(formatBlock("WORKTREE CREATE", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "rollback" -> {
                val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree rollback <worktree-id>")
                val result = worktreeService.rollback(wid)
                ui.renderNotice(formatBlock("WORKTREE ROLLBACK", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "merge" -> {
                val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree merge <worktree-id>")
                val verification = args.getOrNull(2) ?: "git diff --check"
                val result = worktreeService.verifyAndMerge(wid, verification)
                ui.renderNotice(formatBlock("WORKTREE MERGE", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "show" -> {
                val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree show <worktree-id>")
                val wt = worktreeService.readWorktree(wid) ?: return invalid("worktree not found: $wid")
                val text = buildString {
                    appendLine("id: ${wt.id}")
                    appendLine("job: ${wt.jobId}")
                    appendLine("path: ${wt.worktreePath}")
                    appendLine("baseline: ${wt.baselineCommit ?: "none"}")
                    appendLine("territory: ${wt.territory.joinToString(", ").ifEmpty { "none" }}")
                    appendLine("verified: ${wt.verified}")
                    appendLine("rolled back: ${wt.rolledBack}")
                    appendLine("merged back: ${wt.mergedBack}")
                    appendLine("applied patches: ${wt.appliedPatches.size}")
                }.trimEnd()
                ui.renderNotice(formatBlock("WORKTREE", text))
                AgentCommandOutcome.Completed(text)
            }
            else -> invalid("usage: /agent worktree [list|create|rollback|merge|show]")
        }
    }

    // --- Gate (M9) ---
    private fun handleGateCommand(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "check" -> {
                val nodeId = args.getOrNull(1) ?: return invalid("usage: /agent gate check <node-id>")
                val node = dagService.readNode(nodeId) ?: return invalid("node not found: $nodeId")
                val report = completionGate.evaluateNode(node)
                val text = buildString {
                    appendLine("can complete: ${report.canComplete}")
                    appendLine("message: ${report.message}")
                    report.gateResults.forEach { g ->
                        appendLine("  ${if (g.passed) "PASS" else "FAIL"} ${g.gateName}: ${g.detail}")
                    }
                }.trimEnd()
                ui.renderNotice(formatBlock("GATE CHECK", text))
                if (report.canComplete) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            "verify" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent gate verify <dag-id>")
                val falseCompletions = completionGate.detectFalseCompletions(dagId)
                val text = if (falseCompletions.isEmpty()) "no false completions detected" else "false completions: ${falseCompletions.joinToString(", ")}"
                ui.renderNotice(formatBlock("GATE VERIFY", text))
                if (falseCompletions.isEmpty()) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            "complete" -> {
                val nodeId = args.getOrNull(1) ?: return invalid("usage: /agent gate complete <node-id>")
                val node = dagService.readNode(nodeId) ?: return invalid("node not found: $nodeId")
                val newState = completionGate.markCompleteAfterVerification(node)
                val text = "node $nodeId state: $newState"
                ui.renderNotice(formatBlock("GATE COMPLETE", text))
                if (newState == DagNodeState.COMPLETE) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            else -> invalid("usage: /agent gate [check|verify|complete]")
        }
    }

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }

    private data class RunRequest(
        val smokeCommand: String? = null,
        val task: String = ""
    )
}
