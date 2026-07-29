package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNodeState
import atropos.core.verification.VerifiedCompletionGate

class AgentGateCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val dagService: DagExecutionService,
    private val completionGate: VerifiedCompletionGate,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome =
        when (args.getOrNull(0)?.lowercase()) {
            null, "check" -> check(args)
            "verify" -> {
                val dagId = args.getOrNull(1) ?: return invalid("usage: /agent gate verify <dag-id>")
                val falseCompletions = completionGate.detectFalseCompletions(dagId)
                val text = if (falseCompletions.isEmpty()) "no false completions detected" else "false completions: ${falseCompletions.joinToString(", ")}"
                ui.renderNotice(AgentCommandText.formatBlock("GATE VERIFY", text))
                if (falseCompletions.isEmpty()) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
            }
            "complete" -> complete(args)
            else -> invalid("usage: /agent gate [check|verify|complete]")
        }

    private fun check(args: List<String>): AgentCommandOutcome {
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
        ui.renderNotice(AgentCommandText.formatBlock("GATE CHECK", text))
        return if (report.canComplete) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
    }

    private fun complete(args: List<String>): AgentCommandOutcome {
        val nodeId = args.getOrNull(1) ?: return invalid("usage: /agent gate complete <node-id>")
        val node = dagService.readNode(nodeId) ?: return invalid("node not found: $nodeId")
        val newState = completionGate.markCompleteAfterVerification(node)
        val text = "node $nodeId state: $newState"
        ui.renderNotice(AgentCommandText.formatBlock("GATE COMPLETE", text))
        return if (newState == DagNodeState.COMPLETE) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
    }
}
