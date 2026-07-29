package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.AgentDaemonDoctor
import atropos.core.agent.AgentDaemonService

class AgentDaemonCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val daemonService: AgentDaemonService,
    private val activeProviderName: () -> String,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome =
        when (args.getOrNull(0)?.lowercase()) {
            "once" -> once()
            "foreground" -> {
                val result = daemonService.foreground(activeProviderName())
                val rendered = AgentCommandText.formatBlock("AGENT DAEMON FOREGROUND", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "start" -> {
                val result = daemonService.start()
                val rendered = AgentCommandText.formatBlock("AGENT DAEMON START", result.render())
                if (result.ok) ui.renderNotice(rendered) else ui.renderError(rendered)
                if (result.ok) AgentCommandOutcome.Completed(rendered) else AgentCommandOutcome.Invalid(rendered)
            }
            "stop" -> {
                val result = daemonService.stop()
                val rendered = AgentCommandText.formatBlock("AGENT DAEMON STOP", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            null, "status" -> {
                val result = daemonService.status()
                val rendered = AgentCommandText.formatBlock("AGENT DAEMON STATUS", result.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }
            "doctor" -> doctor()
            else -> invalid("usage: /agent daemon [once|foreground|start|stop|status|doctor]")
        }

    private fun once(): AgentCommandOutcome {
        ui.startSpinner("Running daemon once")
        return try {
            val result = daemonService.once(activeProviderName())
            val rendered = AgentCommandText.formatBlock("AGENT DAEMON ONCE", result.render())
            ui.renderNotice(rendered)
            AgentCommandOutcome.Completed(rendered)
        } finally {
            ui.stopSpinner()
        }
    }

    private fun doctor(): AgentCommandOutcome {
        val result = AgentDaemonDoctor().run()
        val rendered = AgentCommandText.formatBlock("AGENT DAEMON DOCTOR", result.render())
        if (result.passed) {
            ui.renderNotice(rendered)
            return AgentCommandOutcome.Completed(rendered)
        }
        ui.renderError(rendered)
        return AgentCommandOutcome.Invalid(rendered)
    }
}
