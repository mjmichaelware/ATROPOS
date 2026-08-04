package atropos.cli.commands

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AgentJobRenderer
import atropos.cli.ui.AgentQueueRenderer
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ContextAttestationRenderer
import atropos.cli.ui.TerminalTheme
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentRunService
import atropos.core.agent.AgentService
import java.nio.file.Path

class AgentJobCommandHandler(
    private val ui: AnsiTerminalEngine,
    repoRoot: Path,
    private val service: AgentService,
    private val runService: AgentRunService,
    private val queueService: AgentQueueService,
    private val activeProviderName: () -> String,
    private val terminalWidth: () -> Int,
    private val currentPatchId: () -> String?,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    private val theme = TerminalTheme(ConfigurationManager())
    private val jobRenderer = AgentJobRenderer(theme)
    private val queueRenderer = AgentQueueRenderer(theme)
    private val attestationRenderer = ContextAttestationRenderer(theme)
    private val jobSummaryMapper = AgentJobSummaryMapper(
        repoRoot.resolve(".atropos/agent/patches").normalize(),
        AgentPatchExtractor()
    )

    fun run(args: List<String>): AgentCommandExecutionResult {
        val runRequest = AgentCommandParser.parseRunRequest(args)
        if (runRequest.task.isBlank()) {
            return invalidResult("usage: /agent run [--smoke <command>] <task>")
        }

        ui.startSpinner("Planning durable agent job")
        return try {
            val result = runService.run(activeProviderName(), runRequest.task, runRequest.smokeCommand)
            val rendered = AgentCommandText.renderRendererOutput(
                jobRenderer.renderRunSummary(jobSummaryMapper.toJobSummary(result), terminalWidth())
            )
            completed(rendered, result.appliedPatchId ?: result.patchId ?: currentPatchId())
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent run failed")
        } finally {
            ui.stopSpinner()
        }
    }

    fun enqueue(args: List<String>): AgentCommandExecutionResult {
        val request = AgentCommandParser.parseRunRequest(args)
        if (request.task.isBlank()) {
            return invalidResult("usage: /agent enqueue [--smoke <command>] <task>")
        }
        val record = queueService.enqueue(request.task, request.smokeCommand)
        return completed(AgentCommandText.renderRendererOutput(queueRenderer.renderDetail(record, terminalWidth())))
    }

    fun status(): AgentCommandExecutionResult {
        val snapshot = service.status(activeProviderName())
        val rendered = AgentCommandText.formatBlock("AGENT STATUS", snapshot.render())
        ui.renderNotice(rendered)
        ui.renderNotice(attestationRenderer.renderStatusRowsFromMemory(ATTESTATION_WIDTH).joinToString("\n"))
        return AgentCommandExecutionResult(
            outcome = AgentCommandOutcome.Completed(rendered),
            lastKnownPatchId = snapshot.lastPatchId ?: currentPatchId()
        )
    }

    fun jobs(): AgentCommandExecutionResult {
        val rendered = AgentCommandText.renderRendererOutput(
            jobRenderer.renderJobsList(runService.listJobs().map { jobSummaryMapper.toJobSummary(it) }, terminalWidth())
        )
        return completed(rendered)
    }

    fun job(args: List<String>): AgentCommandExecutionResult {
        val jobRequest = AgentCommandParser.parseJobRequest(args)
        val jobReference = jobRequest.reference ?: return invalidResult("usage: /agent job [<id|latest>] [--raw]")
        val job = runService.resolveJob(jobReference) ?: return invalidResult("job not found: $jobReference")
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
        return completed(rendered)
    }

    fun verify(args: List<String>): AgentCommandExecutionResult {
        val patchReference = AgentCommandParser.parseReference(args)
            ?: return invalidResult("usage: /agent verify [<patch-id|latest>]")

        ui.startSpinner("Running deterministic verification")
        return try {
            val result = service.verify(patchReference)
            val rendered = AgentCommandText.formatBlock("AGENT VERIFY", result.render())
            completed(rendered, result.patchId ?: currentPatchId())
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent verify failed")
        } finally {
            ui.stopSpinner()
        }
    }

    private fun completed(rendered: String, patchId: String? = null): AgentCommandExecutionResult {
        ui.renderNotice(rendered)
        return AgentCommandExecutionResult(AgentCommandOutcome.Completed(rendered), patchId)
    }

    private fun invalidResult(message: String): AgentCommandExecutionResult =
        AgentCommandExecutionResult(invalid(message))

    private companion object {
        const val ATTESTATION_WIDTH = 80
    }
}
