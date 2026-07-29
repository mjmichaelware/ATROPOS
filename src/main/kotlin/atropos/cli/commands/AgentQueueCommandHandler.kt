package atropos.cli.commands

import atropos.cli.ui.AgentQueueRenderer
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.AgentQueueDoctor
import atropos.core.agent.AgentQueueService

data class AgentQueueCommandResult(
    val outcome: AgentCommandOutcome,
    val lastKnownPatchId: String? = null
)

class AgentQueueCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val queueService: AgentQueueService,
    private val queueRenderer: AgentQueueRenderer,
    private val activeProviderName: () -> String,
    private val terminalWidth: () -> Int,
    private val currentPatchId: () -> String?,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentQueueCommandResult {
        return when (args.getOrNull(0)?.lowercase()) {
            null -> completed(renderList())
            "show" -> show(args.drop(1))
            "run" -> run(args.drop(1))
            "resume" -> resume(args.drop(1))
            "cancel" -> cancel(args.drop(1))
            "recover" -> completed(AgentCommandText.formatBlock("AGENT QUEUE RECOVER", queueService.recover().render()))
            "doctor" -> doctor()
            else -> invalidResult("usage: /agent queue [show <queue-id|latest> [--raw]|run next|run --max <count>|resume <queue-id|latest>|cancel <queue-id|latest>|recover|doctor]")
        }
    }

    private fun show(args: List<String>): AgentQueueCommandResult {
        val request = AgentCommandParser.parseQueueShowRequest(args)
        val reference = request.reference ?: return invalidResult("usage: /agent queue show [<queue-id|latest>] [--raw]")
        val record = queueService.resolve(reference) ?: return invalidResult("queue entry not found: $reference")
        val rendered = if (request.raw) {
            AgentCommandText.formatBlock("AGENT QUEUE RAW", record.renderRaw())
        } else {
            buildString {
                append(AgentCommandText.renderRendererOutput(queueRenderer.renderDetail(record, terminalWidth())))
                appendLine()
                append("raw: /agent queue show ${record.id} --raw")
            }.trimEnd()
        }
        return completed(rendered)
    }

    private fun resume(args: List<String>): AgentQueueCommandResult {
        val reference = AgentCommandParser.parseReference(args) ?: return invalidResult("usage: /agent queue resume [<queue-id|latest>]")
        ui.startSpinner("Resuming queued agent work")
        return try {
            val result = queueService.resume(activeProviderName(), reference)
            val patchId = result.jobRecord?.let { it.appliedPatchId ?: it.patchId ?: currentPatchId() }
            completed(renderQueueRunResult("AGENT QUEUE RESUME", result), patchId)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun cancel(args: List<String>): AgentQueueCommandResult {
        val reference = AgentCommandParser.parseReference(args) ?: return invalidResult("usage: /agent queue cancel [<queue-id|latest>]")
        val record = queueService.cancel(reference) ?: return invalidResult("queue entry not found: $reference")
        return completed(AgentCommandText.renderRendererOutput(queueRenderer.renderDetail(record, terminalWidth())))
    }

    private fun run(args: List<String>): AgentQueueCommandResult {
        return when {
            args.size == 1 && args[0].equals("next", ignoreCase = true) -> runNext()
            args.size == 2 && args[0] == "--max" -> runMax(args[1])
            else -> invalidResult("usage: /agent queue run next | /agent queue run --max <count>")
        }
    }

    private fun runNext(): AgentQueueCommandResult {
        ui.startSpinner("Running next queued agent job")
        return try {
            val result = queueService.runNext(activeProviderName())
            val patchId = result.jobRecord?.let { it.appliedPatchId ?: it.patchId ?: currentPatchId() }
            completed(renderQueueRunResult("AGENT QUEUE RUN", result), patchId)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun runMax(rawMax: String): AgentQueueCommandResult {
        val max = rawMax.toIntOrNull()
            ?: return invalidResult("usage: /agent queue run --max <1-${atropos.core.agent.AgentQueueDefaults.MAX_RUN_COUNT}>")
        ui.startSpinner("Running queued agent batch")
        return try {
            val result = queueService.runMax(activeProviderName(), max)
            val patchId = result.results.mapNotNull { it.jobRecord }.lastOrNull()
                ?.let { it.appliedPatchId ?: it.patchId ?: currentPatchId() }
            completed(AgentCommandText.formatBlock("AGENT QUEUE RUN", result.render()), patchId)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun doctor(): AgentQueueCommandResult {
        val result = AgentQueueDoctor().run()
        val rendered = AgentCommandText.formatBlock("AGENT QUEUE DOCTOR", result.render())
        if (result.passed) {
            ui.renderNotice(rendered)
            return AgentQueueCommandResult(AgentCommandOutcome.Completed(rendered))
        }
        ui.renderError(rendered)
        return AgentQueueCommandResult(AgentCommandOutcome.Invalid(rendered))
    }

    private fun renderList(): String =
        AgentCommandText.renderRendererOutput(queueRenderer.renderList(queueService.list(), terminalWidth()))

    private fun renderQueueRunResult(title: String, result: atropos.core.agent.AgentQueueRunResult): String = buildString {
        appendLine("── $title ──")
        appendLine(result.message)
        result.queueRecord?.let { record ->
            queueRenderer.renderDetail(record, terminalWidth()).forEach { appendLine(it) }
        }
        result.jobRecord?.let { job ->
            appendLine()
            appendLine("job: ${job.id}")
            appendLine("provider: ${job.provider}")
            appendLine("patch: ${job.appliedPatchId ?: job.patchId ?: "none"}")
            appendLine("verification: ${job.verificationId ?: "none"}")
            appendLine("smoke: ${job.smokeResult ?: "none"}")
        }
    }.trimEnd()

    private fun completed(rendered: String, patchId: String? = null): AgentQueueCommandResult {
        ui.renderNotice(rendered)
        return AgentQueueCommandResult(AgentCommandOutcome.Completed(rendered), patchId)
    }

    private fun invalidResult(message: String): AgentQueueCommandResult =
        AgentQueueCommandResult(invalid(message))
}
