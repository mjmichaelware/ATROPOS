/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

data class CommandEntry(
    val command: String,
    val description: String,
    val category: String = "General",
    val aliases: List<String> = emptyList()
)

data class CommandGroup(
    val category: String,
    val entries: List<CommandEntry>
)

object CommandRegistry {
    private val catalog: List<CommandEntry> = listOf(
        CommandEntry("/help", "show command help", "System", aliases = listOf("/usage", "/?")),
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
        CommandEntry(
            "/self-host",
            "shorthand for /agent self-host",
            "Self-host",
            aliases = listOf("/agent self-host")
        ),
        CommandEntry(
            "/self-host run",
            "run Phase 11 self-host loop from a natural-language goal",
            "Self-host",
            aliases = listOf("/agent self-host run")
        ),
        CommandEntry(
            "/self-host start",
            "start a durable self-hosting goal",
            "Self-host",
            aliases = listOf("/agent self-host start")
        ),
        CommandEntry(
            "/self-host status",
            "self-hosting goal status",
            "Self-host",
            aliases = listOf("/agent self-host status")
        ),
        CommandEntry(
            "/self-host watch",
            "watch self-hosting goal events",
            "Self-host",
            aliases = listOf("/agent self-host watch")
        ),
        CommandEntry(
            "/self-host resume",
            "resume the next ready DAG node",
            "Self-host",
            aliases = listOf("/agent self-host resume")
        ),
        CommandEntry(
            "/self-host recover",
            "recover restart state and continue self-hosting",
            "Self-host",
            aliases = listOf("/agent self-host recover")
        ),
        CommandEntry(
            "/self-host next",
            "show next autonomous self-host action",
            "Self-host",
            aliases = listOf("/agent self-host next")
        ),
        CommandEntry(
            "/self-host stop",
            "stop a self-hosting goal",
            "Self-host",
            aliases = listOf("/agent self-host stop")
        ),
        CommandEntry(
            "/self-host verify",
            "verify self-hosting goal state",
            "Self-host",
            aliases = listOf("/agent self-host verify")
        ),
        CommandEntry(
            "/self-host promote",
            "promote a verified candidate JAR safely",
            "Self-host",
            aliases = listOf("/agent self-host promote")
        ),
        CommandEntry(
            "/self-host export-evidence",
            "export self-host evidence bundle",
            "Self-host",
            aliases = listOf("/agent self-host export-evidence")
        ),
        CommandEntry(
            "/self-host history",
            "self-hosting goal history",
            "Self-host",
            aliases = listOf("/agent self-host history")
        ),
        CommandEntry(
            "/self-host learned",
            "learned experiences from self-hosting",
            "Self-host",
            aliases = listOf("/agent self-host learned")
        ),
        CommandEntry(
            "/self-host benchmark",
            "self-hosting benchmark summary",
            "Self-host",
            aliases = listOf("/agent self-host benchmark")
        ),
        CommandEntry("/memory", "memory status"),
        CommandEntry("/paid", "paid emergency gate"),
        CommandEntry("/paid status", "paid emergency status"),
        CommandEntry("/pwd", "show shell cwd"),
        CommandEntry("/cd", "change shell cwd"),
        CommandEntry("/ls", "list files through shell bridge"),
        CommandEntry("/git status", "git status through shell bridge"),
        CommandEntry("/shell", "run explicit shell command"),
        CommandEntry("/exit", "close session"),
        CommandEntry("/quit", "close session"),
        CommandEntry("/director", "director advisory mode status"),
        CommandEntry("/director observe", "record a director observation"),
        CommandEntry("/director report", "advisory report of unacknowledged observations"),
        CommandEntry("/director acknowledge", "acknowledge a director observation"),
        CommandEntry("/director dismiss", "dismiss a director observation"),
        CommandEntry("/director scan", "scan working tree diff for drift and violations"),
        CommandEntry("/territory", "territory enforcement status"),
        CommandEntry("/territory assign", "assign territory to an owner"),
        CommandEntry("/territory revoke", "revoke a territory assignment"),
        CommandEntry("/territory violations", "list territory violations"),
        CommandEntry("/territory resolve", "resolve a territory violation"),
        CommandEntry("/hr", "HR Router status"),
        CommandEntry("/hr route", "route a cross-boundary information request"),
        CommandEntry("/hr audit", "HR Router audit log"),
        CommandEntry("/auditor", "auditor status and report"),
        CommandEntry("/auditor run", "run auditor checks"),
        CommandEntry("/custodian", "custodian status and report"),
        CommandEntry("/custodian clean", "run custodian temp file cleanup"),
        CommandEntry("/custodian prune", "prune dead snapshots"),
        CommandEntry("/hierarchy", "agent hierarchy status"),
        CommandEntry("/hierarchy register", "register an agent in the hierarchy"),
        CommandEntry("/hierarchy snapshot", "current hierarchy snapshot"),
        CommandEntry("/hierarchy escalate", "show escalation path for an agent"),
        CommandEntry("/dag status", "DAG compilation and coverage status"),
        CommandEntry("/dag ingest", "ingest a source document into the DAG"),
        CommandEntry("/dag nodes", "list DAG nodes"),
        CommandEntry("/dag runnable", "list runnable DAG nodes"),
        CommandEntry("/dag cycles", "detect dependency cycles in the DAG"),
        CommandEntry("/dag hig", "Hierarchical Implementation Gap report"),
        CommandEntry("/dag snapshot", "current DAG snapshot"),
        CommandEntry("/snapshot", "multimodal snapshot status"),
        CommandEntry("/snapshot capture", "capture a terminal/file/viewport snapshot"),
        CommandEntry("/snapshot compare", "compare two snapshots"),
        CommandEntry("/snapshot list", "list recent snapshots"),
        CommandEntry("/inspect", "inspection service status"),
        CommandEntry("/inspect file", "inspect file for drift against prior snapshot"),
        CommandEntry("/inspect dag", "verify DAG state against expected completions"),
        CommandEntry("/inspect viewport", "verify viewport content contains expected pattern"),
        CommandEntry("/inspect full", "run full multimodal inspection"),
        CommandEntry("/snapshot report", "inspection report"),
        CommandEntry("/platform", "platform descriptor and environment"),
        CommandEntry("/platform adapters", "list available platform adapters"),
        CommandEntry("/platform health", "platform health status"),
        CommandEntry("/platform env", "platform environment details"),
        CommandEntry("/artifact", "artifact pipeline status"),
        CommandEntry("/artifact plan", "plan an artifact build from prompt"),
        CommandEntry("/artifact build", "build artifacts from plan"),
        CommandEntry("/artifact verify", "verify artifact integrity"),
        CommandEntry("/artifact install", "install artifact to target directory"),
        CommandEntry("/artifact commit", "prepare commit candidate from artifacts"),
        CommandEntry("/artifact gate", "run acceptance gate on artifact"),
        CommandEntry("/autonomous", "autonomous orchestrator status"),
        CommandEntry("/autonomous init", "initialize autonomous session"),
        CommandEntry("/autonomous tick", "run one autonomous orchestrator tick"),
        CommandEntry("/autonomous run", "run one eligible autonomous task"),
        CommandEntry("/autonomous run-max", "run up to N autonomous tasks"),
        CommandEntry("/autonomous backlog", "show autonomous task backlog"),
        CommandEntry("/autonomous repairs", "show repair history"),
        CommandEntry("/autonomous failovers", "show provider failover history")
    )

    val entries: List<CommandEntry> = catalog
        .flatMap { it.expandedEntries() }
        .distinctBy { it.command }

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

    fun quickAccessCommands(): List<String> =
        listOf(
            "/help",
            "/usage",
            "/?",
            "/status",
            "/self-host",
            "/agent self-host",
            "/providers",
            "/route",
            "/use",
            "/verify",
            "/exit"
        )

    fun helpSections(): List<CommandGroup> =
        catalog
            .groupBy { it.category }
            .toSortedMap()
            .map { (category, commands) ->
                CommandGroup(
                    category = category,
                    entries = commands.sortedWith(
                        compareBy<CommandEntry>({ it.command.length }, { it.command })
                    )
                )
            }

    fun helpLines(): List<String> =
        buildList {
            helpSections().forEach { group ->
                add("[${group.category}]")
                group.entries.forEach { entry ->
                    add("  ${entry.command.padEnd(26)} ${entry.description}${entry.aliasSummary()}")
                }
                add("")
            }
        }.dropLastWhile { it.isBlank() }

    fun slashMatches(query: String): List<CommandEntry> {
        return search(query)
    }

    fun search(query: String): List<CommandEntry> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val bare = normalized.removePrefix("/")
        return catalog
            .mapNotNull { entry ->
                val score = entry.matchScore(bare) ?: return@mapNotNull null
                score to entry
            }
            .sortedWith(
                compareBy<Pair<Int, CommandEntry>>(
                    { it.first },
                    { it.second.command.length },
                    { it.second.command }
                )
            )
            .flatMap { it.second.expandedEntries() }
            .distinctBy { it.command }
    }

    private fun CommandEntry.expandedEntries(): List<CommandEntry> =
        buildList {
            add(this@expandedEntries)
            aliases.forEach { alias ->
                add(
                    CommandEntry(
                        command = alias,
                        description = description,
                        category = category,
                        aliases = listOf(command) + aliases.filterNot { it == alias }
                    )
                )
            }
        }

    private fun CommandEntry.matchScore(query: String): Int? {
        val commandText = command.removePrefix("/")
        val aliasTextMatches = aliases.any { alias ->
            aliasText(alias).contains(query, ignoreCase = true)
        }
        val aliasPrefixMatches = aliases.any { alias ->
            aliasText(alias).startsWith(query, ignoreCase = true)
        }
        val aliasExactMatches = aliases.any { alias ->
            aliasText(alias).equals(query, ignoreCase = true)
        }
        val descriptionMatches = description.contains(query, ignoreCase = true)
        val commandStarts = commandText.startsWith(query, ignoreCase = true)
        val commandContains = commandText.contains(query, ignoreCase = true)

        if (!commandStarts && !commandContains && !aliasTextMatches && !descriptionMatches) {
            return null
        }

        return when {
            commandText.equals(query, ignoreCase = true) -> 0
            aliasExactMatches -> 1
            commandStarts -> 2
            aliasPrefixMatches -> 3
            commandContains -> 4
            aliasTextMatches -> 5
            descriptionMatches -> 6
            else -> 7
        }
    }

    private fun aliasText(alias: String): String =
        alias.removePrefix("/")

    private fun CommandEntry.aliasSummary(): String =
        if (aliases.isEmpty()) {
            ""
        } else {
            " (aliases: ${aliases.joinToString(", ")})"
        }
}
