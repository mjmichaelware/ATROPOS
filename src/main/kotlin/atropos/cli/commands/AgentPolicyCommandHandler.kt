package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.policy.AutonomyActionClass
import atropos.core.policy.AutonomyPolicyEngine

class AgentPolicyCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val autonomyAdvisor: AutonomyPolicyEngine,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome =
        when (args.getOrNull(0)?.lowercase()) {
            null, "audit" -> {
                val audit = autonomyAdvisor.latestAudit()
                val text = audit.joinToString("\n") {
                    "${it.decidedAt} ${it.actionClass} advisory_allowed=${it.advisoryAllowed} advisory_blocked=${it.advisoryBlocked} ${it.reason}"
                }.ifEmpty { "no audit records" }
                ui.renderNotice(AgentCommandText.formatBlock("AUTONOMY ADVISORY AUDIT", text))
                AgentCommandOutcome.Completed(text)
            }
            "check" -> {
                val action = args.getOrNull(1)?.let { runCatching { AutonomyActionClass.valueOf(it.uppercase()) }.getOrNull() }
                    ?: return invalid("usage: /agent policy check <ActionClass>")
                val desc = args.drop(2).joinToString(" ")
                val decision = autonomyAdvisor.advise(action, mapOf("description" to desc))
                val text = "action=$action advisory_allowed=${decision.advisoryAllowed} " +
                    "advisory_blocked=${decision.advisoryBlocked} reason=${decision.reason} " +
                    "(advisory only — not an execution permit)"
                ui.renderNotice(AgentCommandText.formatBlock("AUTONOMY ADVICE", text))
                if (decision.advisoryAllowed) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            else -> invalid("usage: /agent policy [audit|check]")
        }
}
