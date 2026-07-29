package atropos.cli.commands

class SelfHostNaturalLanguageRouter {
    fun route(tokens: List<String>): List<String>? {
        if (tokens.size >= 2 && tokens[0].equals("/agent", ignoreCase = true) && tokens[1].equals("self-host", ignoreCase = true)) {
            return null
        }
        val text = tokens.joinToString(" ").trim()
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val namesAtropos = "atropos" in lower
        val asksSelfBuild = listOf(
            "self-host",
            "self host",
            "build itself",
            "build atropos",
            "build yourself",
            "inside out",
            "inside-out"
        ).any { it in lower }
        if (!namesAtropos || !asksSelfBuild) return null
        val asksRecovery = listOf("continue", "resume", "recover", "restart").any { it in lower }
        return if (asksRecovery) {
            listOf("/agent", "self-host", "recover")
        } else {
            listOf("/agent", "self-host", "run") + tokens
        }
    }
}
