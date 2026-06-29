/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

data class CommandEntry(
    val command: String,
    val description: String
)

object CommandRegistry {
    val entries: List<CommandEntry> = listOf(
        CommandEntry("/help", "commands"),
        CommandEntry("/dashboard", "return to dashboard"),
        CommandEntry("/status", "session status"),
        CommandEntry("/status adapters", "provider adapter matrix"),
        CommandEntry("/status assets", "asset route status"),
        CommandEntry("/status endpoints", "operation registry"),
        CommandEntry("/status failures", "provider failure summary"),
        CommandEntry("/status quota", "quota ledger"),
        CommandEntry("/providers", "provider inventory"),
        CommandEntry("/providers descriptors", "provider contract grid"),
        CommandEntry("/providers validate", "provider descriptor validation"),
        CommandEntry("/route", "preview routing decision"),
        CommandEntry("/use", "switch provider"),
        CommandEntry("/use auto", "automatic routing"),
        CommandEntry("/verify", "verification scope"),
        CommandEntry("/verify narrow", "quick verification"),
        CommandEntry("/verify wide", "wide verification"),
        CommandEntry("/keys", "key status"),
        CommandEntry("/keys setup", "local secret template setup"),
        CommandEntry("/keys status", "key source status"),
        CommandEntry("/factory", "factory status"),
        CommandEntry("/factory plan", "bounded app-factory plan"),
        CommandEntry("/factory run", "queue app-factory run"),
        CommandEntry("/memory", "memory status"),
        CommandEntry("/paid", "paid emergency gate"),
        CommandEntry("/paid status", "paid emergency status"),
        CommandEntry("/pwd", "show shell cwd"),
        CommandEntry("/cd", "change shell cwd"),
        CommandEntry("/ls", "list files through shell bridge"),
        CommandEntry("/git status", "git status through shell bridge"),
        CommandEntry("/shell", "run explicit shell command"),
        CommandEntry("/exit", "close session"),
        CommandEntry("/quit", "close session")
    ).distinctBy { it.command }

    val providers: List<String> = listOf(
        "anthropic",
        "groq",
        "openrouter",
        "deepinfra",
        "siliconflow",
        "gemini",
        "github_models",
        "cloudflare_ai",
        "cloudflare_workers",
        "jina",
        "serpapi",
        "supabase",
        "pinecone",
        "google_drive",
        "github_actions",
        "google_cloud_free",
        "huggingface",
        "fal",
        "replicate",
        "ollama",
        "openai",
        "xai",
        "local"
    )

    fun commands(): List<String> =
        entries.map { it.command }

    fun helpLines(): List<String> =
        entries.map {
            "  ${it.command.padEnd(26)} ${it.description}"
        }

    fun slashMatches(query: String): List<CommandEntry> {
        val normalized = query.trimStart()
        if (!normalized.startsWith("/")) return emptyList()

        val bare = normalized.removePrefix("/")
        return entries.filter { entry ->
            entry.command.startsWith(normalized) ||
                entry.command.contains(
                    normalized,
                    ignoreCase = true
                ) ||
                entry.description.contains(
                    bare,
                    ignoreCase = true
                )
        }
    }
}
