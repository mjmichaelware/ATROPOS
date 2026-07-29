package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.worktree.IsolatedWorktreeService

class AgentWorktreeCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val worktreeService: IsolatedWorktreeService,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome =
        when (args.getOrNull(0)?.lowercase()) {
            null, "list" -> {
                val text = worktreeService.listWorktrees()
                    .joinToString("\n") { "${it.id}: job=${it.jobId} verified=${it.verified} rolledBack=${it.rolledBack} merged=${it.mergedBack}" }
                    .ifEmpty { "no worktrees" }
                ui.renderNotice(AgentCommandText.formatBlock("WORKTREES", text))
                AgentCommandOutcome.Completed(text)
            }
            "create" -> create(args)
            "rollback" -> {
                val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree rollback <worktree-id>")
                val result = worktreeService.rollback(wid)
                ui.renderNotice(AgentCommandText.formatBlock("WORKTREE ROLLBACK", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "merge" -> {
                val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree merge <worktree-id>")
                val verification = args.getOrNull(2) ?: "git diff --check"
                val result = worktreeService.verifyAndMerge(wid, verification)
                ui.renderNotice(AgentCommandText.formatBlock("WORKTREE MERGE", result.message))
                if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
            }
            "show" -> show(args)
            else -> invalid("usage: /agent worktree [list|create|rollback|merge|show]")
        }

    private fun create(args: List<String>): AgentCommandOutcome {
        val jobId = args.getOrNull(1) ?: return invalid("usage: /agent worktree create <job-id> [--territory path,...]")
        val territoryIdx = args.indexOf("--territory")
        val territory = if (territoryIdx >= 0) args.getOrNull(territoryIdx + 1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList() else emptyList()
        val result = worktreeService.createWorktree(jobId, territory)
        ui.renderNotice(AgentCommandText.formatBlock("WORKTREE CREATE", result.message))
        return if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
    }

    private fun show(args: List<String>): AgentCommandOutcome {
        val wid = args.getOrNull(1) ?: return invalid("usage: /agent worktree show <worktree-id>")
        val wt = worktreeService.readWorktree(wid) ?: return invalid("worktree not found: $wid")
        val text = buildString {
            appendLine("id: ${wt.id}")
            appendLine("job: ${wt.jobId}")
            appendLine("path: ${wt.worktreePath}")
            appendLine("baseline: ${wt.baselineCommit ?: "none"}")
            appendLine("territory: ${wt.territory.joinToString(", ").ifEmpty { "none" }}")
            appendLine("verified: ${wt.verified}")
            appendLine("rolled back: ${wt.rolledBack}")
            appendLine("merged back: ${wt.mergedBack}")
            appendLine("applied patches: ${wt.appliedPatches.size}")
        }.trimEnd()
        ui.renderNotice(AgentCommandText.formatBlock("WORKTREE", text))
        return AgentCommandOutcome.Completed(text)
    }
}
