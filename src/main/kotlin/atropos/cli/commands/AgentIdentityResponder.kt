package atropos.cli.commands

import atropos.core.agent.AgentService
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalRunRecord
import atropos.core.agent.SupervisedSessionState
import atropos.core.agent.SupervisedSessionStore
import java.nio.file.Path

class AgentIdentityResponder(
    private val repoRoot: Path,
    private val service: AgentService,
    private val continuationService: GoalContinuationService,
    private val sessionStore: SupervisedSessionStore,
    private val activeProviderName: () -> String
) {
    fun respond(task: String): String? {
        val lower = task.trim().lowercase()
        if (isExplicitMythologyRequest(lower)) return null
        if (isAtroposIdentityProbe(lower)) return renderRuntimeState()
        if (lower.startsWith("fix atropos")) return renderFixGuidance()
        return null
    }

    private fun isExplicitMythologyRequest(lower: String): Boolean =
        (lower.contains("greek") || lower.contains("mythology") || lower.contains("myth")) &&
            (lower.contains("atropos") || lower.contains("fate") || lower.contains("moirai"))

    private fun isAtroposIdentityProbe(lower: String): Boolean =
        lower == "atropos" ||
            lower == "what is atropos" ||
            lower == "what is atropos doing?" ||
            lower == "what is atropos doing" ||
            lower == "who is atropos" ||
            lower == "who are you" ||
            lower == "what are you" ||
            lower == "tell me about yourself"

    private fun renderRuntimeState(): String {
        val snapshot = service.status(activeProviderName())
        val goals = continuationService.listRuns(Int.MAX_VALUE).runs
        val selfHostGoals = goals.filter { it.provider == "self-host" }
        val sessions = sessionStore.listSessions()
        val activeSessions = sessions.count { it.state == SupervisedSessionState.IDLE || it.state == SupervisedSessionState.BUSY }

        return buildString {
            appendLine("ATROPOS runtime state")
            appendLine()
            appendLine("Repository: ${repoRoot.fileName}")
            appendLine("Repository root: $repoRoot")
            appendLine("Active provider: ${snapshot.activeProvider}")
            appendLine("Provider order: ${snapshot.providerOrder.joinToString(" -> ").ifBlank { "none" }}")
            appendLine("Patch provider order: ${snapshot.patchProviderOrder.joinToString(" -> ").ifBlank { "none" }}")
            appendLine("Last patch: ${snapshot.lastPatchId ?: "none"}")
            appendLine("Owns repo read/write: ${if (snapshot.ownsRepoReadWrite) "yes" else "no"}")
            appendLine("Self-host goals: ${selfHostGoals.size}")
            selfHostGoals.firstOrNull()?.let { appendLine(it.renderSelfHostSummary()) }
            appendLine()
            if (goals.isNotEmpty()) {
                appendLine("Recent goal runs: ${goals.size}")
                goals.take(3).forEach { goal -> appendLine("  ${goal.id}: ${goal.status} (phase ${goal.activePhase ?: "?"})") }
            }
            if (activeSessions > 0) appendLine("Active provider sessions: $activeSessions")
            appendLine()
            appendLine("Type /help to see available commands.")
            appendLine("Type /agent status for detailed agent state.")
            appendLine("Type /status route for provider routing.")
        }.trimEnd()
    }

    private fun GoalRunRecord.renderSelfHostSummary(): String =
        "Self-host current: $id $status phase=${activePhase ?: "none"} node=${currentNodeId ?: "none"}"

    private fun renderFixGuidance(): String = buildString {
        appendLine("ATROPOS is the current repository and autonomous software engine.")
        appendLine()
        appendLine("To fix something specific, describe the task. For example:")
        appendLine("  /agent ask refactor the prompt builder in AgentPromptContract.kt")
        appendLine("  /agent patch add null check to AgentService.ask()")
        appendLine("  /agent run --smoke './gradlew compileKotlin' fix the compile error")
        appendLine()
        appendLine("All work remains inside this repository at:")
        appendLine("  $repoRoot")
    }.trimEnd()
}
