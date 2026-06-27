package atropos.core.swarm

import atropos.core.AIProvider

class DirectorOrchestrator(private val directorNode: AIProvider) {

    fun blueprintExecutionGraph(specification: String): List<String> {
        val prompt = """
            You are the strategic director node.
            Return ONLY a newline-separated list of Kotlin file paths.
            Rules:
            1. One path per line.
            2. Use paths like src/main/kotlin/atropos/...
            3. No markdown.
            4. No commentary.
            5. Order by dependency: contracts, models, engines, UI last.
        """.trimIndent()

        val raw = directorNode.complete(prompt, specification)
        return parsePaths(raw)
    }

    internal fun parsePaths(raw: String): List<String> {
        return raw.lineSequence()
            .map { it.trim() }
            .map { it.removePrefix("-").trim() }
            .map { it.removePrefix("*").trim() }
            .map { it.removePrefix("`").removeSuffix("`").trim() }
            .filter { it.startsWith("src/main/") && it.endsWith(".kt") }
            .distinct()
            .toList()
    }
}
