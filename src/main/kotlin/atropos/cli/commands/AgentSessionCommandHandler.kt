package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.AgentRuntimeKind
import atropos.core.agent.ProviderSessionSupervisor

class AgentSessionCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val sessionSupervisor: ProviderSessionSupervisor,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
                null, "status" -> {
                    val text = sessionSupervisor.status()
                    ui.renderNotice(AgentCommandText.formatBlock("SESSIONS", text))
                    AgentCommandOutcome.Completed(text)
                }
                "create" -> {
                    val result = sessionSupervisor.createSession(AgentRuntimeKind.OPENCODE, args.getOrNull(1)?.toIntOrNull())
                    ui.renderNotice(AgentCommandText.formatBlock("SESSION CREATE", result.message))
                    if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
                }
                "connect" -> connect(args)
                "mark" -> mark(args)
                "heartbeat" -> {
                    val sid = args.getOrNull(1) ?: return invalid("usage: /agent session heartbeat <session-id>")
                    val result = sessionSupervisor.heartbeat(sid)
                    ui.renderNotice(AgentCommandText.formatBlock("SESSION HEARTBEAT", result.message))
                    if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
                }
                "show" -> {
                    val sid = args.getOrNull(1) ?: return invalid("usage: /agent session show <session-id>")
                    val record = sessionSupervisor.readSession(sid) ?: return invalid("session not found: $sid")
                    ui.renderNotice(AgentCommandText.formatBlock("SESSION", record.render()))
                    AgentCommandOutcome.Completed(record.render())
                }
                else -> invalid("usage: /agent session [status|create|connect|mark|heartbeat|show]")
            }
    }

    private fun connect(args: List<String>): AgentCommandOutcome {
        val sid = args.getOrNull(1) ?: return invalid("usage: /agent session connect <session-id> <provider-session-id>")
        val psid = args.getOrNull(2) ?: return invalid("usage: /agent session connect <session-id> <provider-session-id>")
        val result = sessionSupervisor.connectSession(sid, psid)
        ui.renderNotice(AgentCommandText.formatBlock("SESSION CONNECT", result.message))
        return if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
    }

    private fun mark(args: List<String>): AgentCommandOutcome {
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
        ui.renderNotice(AgentCommandText.formatBlock("SESSION MARK", result.message))
        return if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
    }
}
