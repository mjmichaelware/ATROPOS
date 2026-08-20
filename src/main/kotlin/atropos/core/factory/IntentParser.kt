package atropos.core.factory

import java.util.Locale

/** Canonical deterministic parser for general application-generation intent. */
class IntentParser(
    private val actionRegistry: AppActionRegistry = AppActionRegistry()
) {
    fun isAppRequest(prompt: String): Boolean = actionRegistry.isAppRequest(tokenize(prompt))

    fun parse(prompt: String): AppIntent {
        val clean = prompt.trim().ifBlank { "build a local app" }
        val words = tokenize(clean)
        val kind = when {
            words.any { it in WEB_WORDS } -> "web"
            words.any { it in SERVICE_WORDS } -> "service"
            words.any { it in DESKTOP_WORDS } -> "desktop"
            else -> "cli"
        }
        // Both the name and the features come from the same filter, so a gap in
        // the stop-word vocabulary corrupts both at once: "build me a todo list
        // app" named the application `me` and spent half the twelve-feature
        // budget on function words. See [AppPromptStopWords].
        // A document is not a sentence, and reading it as one produced
        // nonsense: the first twelve meaningful words of a ten-kilobyte build
        // specification became the application's commands, so the generated
        // CLI offered `built`, `during` and `monetization` as things to run.
        val declared = DeclaredProjectTree.read(clean)
        val name = DeclaredProjectTree.rootOf(clean)?.trimEnd('/')?.lowercase(Locale.US)
            ?: AppPromptStopWords.firstMeaningful(words, actionRegistry::isAction)
            ?: "generated-app"
        val features = when {
            // The directories a declared tree groups its code into are the
            // functional surfaces the document is describing.
            declared.isNotEmpty() -> surfacesOf(declared)
            // Otherwise, for a document, what it talks about most -- which is
            // deterministic, and unlike word order actually says what it is
            // about.
            isADocument(clean) -> byFrequency(words)
            else -> words
                .filter { !AppPromptStopWords.isStopWord(it) && !actionRegistry.isAction(it) && it.length > 1 }
                .distinct()
                .take(12)
        }
        return AppIntent(name, kind, features)
    }

    /** Long enough and broken into enough lines that it is a document, not a request. */
    private fun isADocument(prompt: String): Boolean =
        prompt.length >= DOCUMENT_CHARACTERS && prompt.count { it == '\n' } >= DOCUMENT_LINES

    private fun surfacesOf(declared: List<DeclaredProjectTree.Entry>): List<String> =
        declared.filter { it.isDirectory }
            .map { it.path.substringAfterLast('/') }
            .filter { it.length > 1 && it !in NON_SURFACE_DIRECTORIES && !it.startsWith(".") }
            .map { it.lowercase(Locale.US) }
            .distinct()
            .take(12)

    private fun byFrequency(words: List<String>): List<String> = words
        .filter { !AppPromptStopWords.isStopWord(it) && !actionRegistry.isAction(it) && it.length > 2 }
        .groupingBy { it }
        .eachCount()
        .entries
        // Count first, then the word itself, so the same document always
        // produces the same commands.
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
        .take(12)

    fun tokenize(prompt: String): List<String> = prompt.lowercase(Locale.US)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    private companion object {
        val WEB_WORDS = setOf("web", "website", "frontend")
        val SERVICE_WORDS = setOf("api", "service", "backend")
        val DESKTOP_WORDS = setOf("desktop", "android", "mobile")

        /** Directories that hold no feature of the application itself. */
        val NON_SURFACE_DIRECTORIES = setOf(
            "tests", "test", "spec", "docs", "doc", "scripts", "static", "templates",
            "vendor", "build", "dist", "assets", "img", "css", "js", "fixtures",
            "src", "app", "lib", "bin", "marketing", "blog"
        )

        const val DOCUMENT_CHARACTERS = 600
        const val DOCUMENT_LINES = 8
    }
}
