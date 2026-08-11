package atropos.core.factory

import java.util.Locale

class AppProjectSpecParser {
    private val actionRegistry = AppActionRegistry()

    fun isAppRequest(prompt: String): Boolean = actionRegistry.isAppRequest(tokenize(prompt))

    fun parse(prompt: String): AppProjectSpec {
        val clean = prompt.trim().ifBlank { "build a local app" }
        val words = tokenize(clean)
        val kind = when {
            words.any { it in setOf("web", "website", "frontend") } -> "web"
            words.any { it in setOf("api", "service", "backend") } -> "service"
            words.any { it in setOf("desktop", "android", "mobile") } -> "desktop"
            else -> "cli"
        }
        val name = words.firstOrNull { it !in STOP_WORDS && !actionRegistry.isAction(it) } ?: "generated-app"
        val features = words.filter { it !in STOP_WORDS && !actionRegistry.isAction(it) }.distinct().take(12)
        return AppProjectSpec(clean, AppIntent(name, kind, features), testRequired = true)
    }

    private fun tokenize(prompt: String): List<String> = prompt.lowercase(Locale.US)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

    private companion object {
        val STOP_WORDS = setOf(
            "a", "an", "the", "simple", "small", "local", "with", "and", "for", "tests", "test", "readme",
            "cli", "web", "website", "frontend", "api", "service", "backend", "desktop", "android", "mobile",
            "app", "application", "project", "repository", "repo"
        )
    }
}
