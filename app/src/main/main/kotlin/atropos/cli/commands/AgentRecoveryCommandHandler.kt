package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.recovery.CrashRecoveryService

class AgentRecoveryCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val recoveryService: CrashRecoveryService
) {
    fun execute(): AgentCommandOutcome {
        val report = recoveryService.recover()
        val text = recoveryService.renderReport(report)
        ui.renderNotice(AgentCommandText.formatBlock("RECOVERY", text))
        return if (report.errors.isEmpty()) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
    }
}
