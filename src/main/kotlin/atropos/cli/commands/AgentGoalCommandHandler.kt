package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalTerminalCondition

class AgentGoalCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val continuationService: GoalContinuationService,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
                null, "list" -> {
                    val runs = continuationService.listRuns()
                    ui.renderNotice(AgentCommandText.formatBlock("GOAL RUNS", runs.message + "\n" + runs.runs.joinToString("\n") { it.renderSummaryLine() }))
                    AgentCommandOutcome.Completed(runs.message)
                }
                "start" -> {
                    val task = args.drop(1).joinToString(" ").ifBlank { return invalid("usage: /agent goal start <task>") }
                    val run = continuationService.startRun(task)
                    ui.renderNotice(AgentCommandText.formatBlock("GOAL START", "run: ${run.id}"))
                    AgentCommandOutcome.Completed("started: ${run.id}")
                }
                "complete" -> complete(args)
                "show" -> {
                    val rid = args.getOrNull(1) ?: return invalid("usage: /agent goal show <run-id>")
                    val run = continuationService.resolveRun(rid) ?: return invalid("run not found: $rid")
                    ui.renderNotice(AgentCommandText.formatBlock("GOAL RUN", run.render()))
                    AgentCommandOutcome.Completed(run.render())
                }
                else -> invalid("usage: /agent goal [list|start|complete|show]")
            }
    }

    private fun complete(args: List<String>): AgentCommandOutcome {
        val rid = args.getOrNull(1) ?: return invalid("usage: /agent goal complete <run-id> <condition>")
        val condition = args.getOrNull(2)
            ?.let { runCatching { GoalTerminalCondition.valueOf(it.uppercase()) }.getOrNull() }
            ?: GoalTerminalCondition.VERIFIED_COMPLETE
        val result = continuationService.completeRun(rid, condition)
        ui.renderNotice(AgentCommandText.formatBlock("GOAL COMPLETE", result.message))
        return if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
    }
}
