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
        val name = words.firstOrNull { it !in STOP_WORDS && !actionRegistry.isAction(it) } ?: "generated-app"
        val features = words.filter { it !in STOP_WORDS && !actionRegistry.isAction(it) }.distinct().take(12)
        return AppIntent(name, kind, features)
    }

    fun tokenize(prompt: String): List<String> = prompt.lowercase(Locale.US)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    private companion object {
        val WEB_WORDS = setOf("web", "website", "frontend")
        val SERVICE_WORDS = setOf("api", "service", "backend")
        val DESKTOP_WORDS = setOf("desktop", "android", "mobile")
        val STOP_WORDS = setOf(
            "a", "an", "the", "simple", "small", "local", "with", "and", "for", "tests", "test", "readme",
            "cli", "web", "website", "frontend", "api", "service", "backend", "desktop", "android", "mobile",
            "app", "application", "project", "repository", "repo"
        )
    }
}
