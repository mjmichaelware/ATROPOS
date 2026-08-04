package atropos.cli.commands

import atropos.core.factory.AppActionRegistry

class SelfHostNaturalLanguageRouter(
    private val appActions: AppActionRegistry = AppActionRegistry()
) {
    fun route(tokens: List<String>): List<String>? {
        if (tokens.size >= 2 && tokens[0].equals("/agent", ignoreCase = true) && tokens[1].equals("self-host", ignoreCase = true)) {
            return null
        }
        val text = tokens.joinToString(" ").trim()
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val namesAtropos = "atropos" in lower
        val addressesRuntime = namesAtropos || "yourself" in lower || "itself" in lower
        val explicitSelfHost = "self-host" in lower || "self host" in lower
        val asksSelfBuild = listOf(
            "self-host",
            "self host",
            "build itself",
            "build atropos",
            "build yourself",
            "improve itself",
            "improve atropos",
            "improve yourself",
            "run self-host",
            "run self host",
            "inside out",
            "inside-out"
        ).any { it in lower }
        if ((!addressesRuntime && !explicitSelfHost) || !asksSelfBuild) {
            return if (appActions.isAppRequest(tokens)) listOf("/factory", "run") + tokens else null
        }
        val asksRecovery = listOf("continue", "resume", "recover", "restart").any { it in lower }
        return if (asksRecovery) {
            listOf("/agent", "self-host", "recover")
        } else {
            listOf("/agent", "self-host", "run") + tokens
        }
    }

}
