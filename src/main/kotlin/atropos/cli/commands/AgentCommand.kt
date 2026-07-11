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
    private val daemonService: AgentDaemonService = AgentDaemonService(config)
) : AgentCommandHandler {
    private val patchExtractor = AgentPatchExtractor()
    private val jobRenderer = AgentJobRenderer(TerminalTheme(ConfigurationManager()))
    private val queueRenderer = AgentQueueRenderer(TerminalTheme(ConfigurationManager()))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val repoRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val patchDirectory = repoRoot.resolve(".atropos/agent/patches").normalize()

    /** Last patch id ATROPOS has knowledge of, surfaced to the status line. Never implies a patch was applied. */
    var lastKnownPatchId: String? = null
        private set

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

            else -> invalid(agentUsage())
        }
    }

    private fun agentUsage(): String =
        "usage: /agent [status|run [--smoke <command>] <task>|enqueue [--smoke <command>] <task>|queue [show|run|resume|cancel|recover|doctor]|daemon [once|foreground|start|stop|status|doctor]|jobs|job <id> [--raw]|ask <task>|patch [--provider <name>] <task>|apply [--check|--verify] <patch-id|latest>|verify [<patch-id|latest>]|repair [<patch-id|latest>]]"

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

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }

    private data class RunRequest(
        val smokeCommand: String? = null,
        val task: String = ""
    )
}
