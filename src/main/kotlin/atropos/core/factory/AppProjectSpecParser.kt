package atropos.core.factory

class AppProjectSpecParser {
    private val intentParser = IntentParser()

    fun isAppRequest(prompt: String): Boolean = intentParser.isAppRequest(prompt)

    /**
     * SD7-R-012: Intent parser that never short-circuits atom graphs.
     * Parsed intent always flows into SpecGraph atomization, never into a template path.
     */
    fun parse(prompt: String): AppProjectSpec {
        val clean = prompt.trim().ifBlank { "build a local app" }
        val parsedIntent = intentParser.parse(clean)
        return AppProjectSpec(
            prompt = clean,
            intent = parsedIntent,
            testRequired = true,
            standardFiles = listOf("README.md", "LICENSE", ".gitignore", "AGENTS.md")
        )
    }
}
