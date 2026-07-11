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
        CommandEntry("/home", "return to dashboard"),
        CommandEntry("/tabs", "list open tabs"),
        CommandEntry("/tab new", "open a new tab"),
        CommandEntry("/tab rename", "rename a tab"),
        CommandEntry("/tab close", "close a tab"),
        CommandEntry("/tab next", "switch to next tab"),
        CommandEntry("/tab prev", "switch to previous tab"),
        CommandEntry("/status", "session status"),
        CommandEntry("/status adapters", "provider adapter matrix"),
        CommandEntry("/status assets", "asset route status"),
        CommandEntry("/status endpoints", "operation registry"),
        CommandEntry("/status failures", "provider failure summary"),
        CommandEntry("/status quota", "quota ledger"),
        CommandEntry("/providers", "provider inventory"),
        CommandEntry("/providers descriptors", "provider contract grid"),
        CommandEntry("/providers validate", "provider descriptor validation"),
        CommandEntry("/providers verify", "offline provider verification (use <id> or all)"),
        CommandEntry("/providers live-test", "explicit free/credit provider live test"),
        CommandEntry("/agent", "agent bridge"),
        CommandEntry("/agent status", "agent bridge status"),
        CommandEntry("/agent run", "durable agent job runner"),
        CommandEntry("/agent run --smoke", "durable agent job runner with local smoke"),
        CommandEntry("/agent enqueue", "durable agent queue enqueue"),
        CommandEntry("/agent enqueue --smoke", "durable agent queue enqueue with local smoke"),
        CommandEntry("/agent queue", "recent durable agent queue entries"),
        CommandEntry("/agent queue show", "durable agent queue entry details"),
        CommandEntry("/agent queue run next", "run one eligible queued agent item"),
        CommandEntry("/agent queue run --max", "run a bounded number of queued agent items"),
        CommandEntry("/agent queue resume", "resume a nonterminal queued agent item"),
        CommandEntry("/agent queue cancel", "cancel a queued or running agent item"),
        CommandEntry("/agent queue recover", "recover stale durable queue leases"),
        CommandEntry("/agent queue doctor", "run isolated durable queue diagnostics"),
        CommandEntry("/agent daemon once", "run one queued agent item through daemon control"),
        CommandEntry("/agent daemon foreground", "run local queue daemon in foreground"),
        CommandEntry("/agent daemon start", "start local queue daemon process"),
        CommandEntry("/agent daemon stop", "request local queue daemon stop"),
        CommandEntry("/agent daemon status", "local queue daemon status"),
        CommandEntry("/agent daemon doctor", "isolated local daemon diagnostics"),
        CommandEntry("/agent jobs", "recent agent jobs"),
        CommandEntry("/agent job", "agent job details (use --raw for full record)"),
        CommandEntry("/agent job --raw", "full raw agent job record"),
        CommandEntry("/agent ask", "agent bridge ask"),
        CommandEntry("/agent patch", "agent bridge patch placeholder"),
        CommandEntry("/agent apply", "agent bridge safe patch apply"),
        CommandEntry("/agent apply --check", "agent bridge patch validation"),
        CommandEntry("/agent apply --verify", "agent bridge patch apply and verify"),
        CommandEntry("/agent verify", "agent bridge verification"),
        CommandEntry("/agent repair", "agent bridge repair"),
        CommandEntry("/route", "preview routing decision"),
        CommandEntry("/dloi lookup", "exact source lookup by document/section/line address"),
        CommandEntry("/dloi resolve", "resolve a task to an authoritative source section"),
        CommandEntry("/ast lookup", "exact Kotlin symbol lookup"),
        CommandEntry("/use", "switch provider"),
        CommandEntry("/use auto", "automatic routing"),
        CommandEntry("/verify", "verification scope"),
        CommandEntry("/verify narrow", "quick verification"),
        CommandEntry("/verify wide", "wide verification"),
        CommandEntry("/keys", "key status"),
        CommandEntry("/keys setup", "local secret template setup"),
        CommandEntry("/keys status", "key source status"),
        CommandEntry("/keys doctor", "key precedence and provider impact doctor"),
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
        "sambanova",
        "deepseek_direct",
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
