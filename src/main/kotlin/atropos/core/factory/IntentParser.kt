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
        val name = AppPromptStopWords.firstMeaningful(words, actionRegistry::isAction) ?: "generated-app"
        val features = words
            .filter { !AppPromptStopWords.isStopWord(it) && !actionRegistry.isAction(it) && it.length > 1 }
            .distinct()
            .take(12)
        return AppIntent(name, kind, features)
    }

    fun tokenize(prompt: String): List<String> = prompt.lowercase(Locale.US)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    private companion object {
        val WEB_WORDS = setOf("web", "website", "frontend")
        val SERVICE_WORDS = setOf("api", "service", "backend")
        val DESKTOP_WORDS = setOf("desktop", "android", "mobile")
    }
}
