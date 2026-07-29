package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.GoalContinuationService
import atropos.core.journal.EventJournalService
import atropos.core.observability.RunObserver

class AgentObservationCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val observer: RunObserver,
    private val journal: EventJournalService,
    private val continuationService: GoalContinuationService,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun runs(): AgentCommandOutcome {
        val text = observer.listRuns()
        ui.renderNotice(AgentCommandText.formatBlock("RUNS", text))
        return AgentCommandOutcome.Completed(text)
    }

    fun watch(args: List<String>): AgentCommandOutcome =
        renderObserved(args, "WATCH") { observerRunId -> journal.readEvents(observerRunId, 20).joinToString("\n") { it.render() } }

    fun tree(args: List<String>): AgentCommandOutcome =
        renderObserved(args, "TREE") { observer.tree(it) }

    fun transcript(args: List<String>): AgentCommandOutcome =
        renderObserved(args, "TRANSCRIPT") { observer.transcript(it) }

    fun diff(args: List<String>): AgentCommandOutcome =
        renderObserved(args, "DIFF") { observer.diffLog(it) }

    fun tests(args: List<String>): AgentCommandOutcome =
        renderObserved(args, "TESTS") { observer.testLog(it) }

    fun observe(args: List<String>): AgentCommandOutcome =
        when (args.getOrNull(0)?.lowercase()) {
            null, "status" -> {
                val state = observer.status()
                val text = buildString {
                    append("port=${state.dashboardPort} running=${state.running} clients=${state.connectedClients}")
                    state.lastError?.let { append(" lastError=$it") }
                }
                ui.renderNotice(AgentCommandText.formatBlock("OBSERVER", text))
                AgentCommandOutcome.Completed("observer status: $text")
            }
            "start" -> {
                val msg = observer.start(args.getOrNull(1)?.toIntOrNull() ?: 4197)
                ui.renderNotice(AgentCommandText.formatBlock("OBSERVER START", msg))
                AgentCommandOutcome.Completed(msg)
            }
            "stop" -> {
                val msg = observer.stop()
                ui.renderNotice(AgentCommandText.formatBlock("OBSERVER STOP", msg))
                AgentCommandOutcome.Completed(msg)
            }
            "open" -> open()
            else -> invalid("usage: /agent observe [status|start|stop|open]")
        }

    private fun renderObserved(
        args: List<String>,
        title: String,
        body: (String) -> String
    ): AgentCommandOutcome {
        val ref = args.getOrNull(0) ?: "latest"
        val runId = resolveObservedRunId(ref) ?: return invalid(if (title == "WATCH") "no runs to watch" else "no runs")
        val text = body(runId)
        ui.renderNotice(AgentCommandText.formatBlock("$title $runId", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun open(): AgentCommandOutcome {
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
        ui.renderNotice(AgentCommandText.formatBlock("OBSERVER OPEN", msg))
        return AgentCommandOutcome.Completed(msg)
    }

    private fun resolveObservedRunId(reference: String): String? {
        if (!reference.equals("latest", ignoreCase = true)) return reference
        return journal.latestRunId() ?: continuationService.latestRun()?.id
    }
}
