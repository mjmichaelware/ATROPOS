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
        CommandEntry("/project", "durable project registry"),
        CommandEntry("/projects", "durable project registry"),
        CommandEntry("/project list", "list registered projects"),
        CommandEntry("/project new", "register a project (use <name> [objective])"),
        CommandEntry("/project show", "project detail by id"),
        CommandEntry("/project status", "set project status (use <id> <status>)"),
        CommandEntry("/project objective", "set a project objective (use <id> <text>)"),
        CommandEntry("/project history", "permanent project event history"),
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
        CommandEntry("/tests", "run the built-in test matrix"),
        CommandEntry("/tests matrix", "run the built-in test matrix"),
        CommandEntry("/keys", "key status"),
        CommandEntry("/keys setup", "local secret template setup"),
        CommandEntry("/keys status", "key source status"),
        CommandEntry("/keys doctor", "key precedence and provider impact doctor"),
        CommandEntry("/security", "secret precedence and redaction status"),
        CommandEntry("/security redact", "redaction report for supplied text"),
        CommandEntry("/factory", "factory status"),
        CommandEntry("/factory plan", "bounded app-factory plan"),
        CommandEntry("/factory run", "queue app-factory run"),
        CommandEntry("/ci", "ci and edge execution status"),
        CommandEntry("/ci local compile", "queue a local compile job"),
        CommandEntry("/ci run next", "run the next queued ci job"),
        CommandEntry("/ops", "deployment descriptor operations"),
        CommandEntry("/ops export", "export deployment descriptor files"),
        CommandEntry("/ops verify", "verify exported deployment descriptors"),
        CommandEntry("/ops quota-backup", "back up the quota ledger"),
        CommandEntry("/ops quota-restore", "restore the quota ledger (use <backup-file>)"),
        CommandEntry("/assets", "asset generator status"),
        CommandEntry("/assets status", "asset generator status"),
        CommandEntry("/assets text", "write a text asset (use <name> <prompt>)"),
        CommandEntry("/assets ansi", "write an ansi asset (use <name> <prompt>)"),
        CommandEntry("/assets svg", "write an svg asset (use <name> <prompt>)"),
        CommandEntry("/agent self-host run", "run Phase 11 self-host loop from a natural-language goal"),
        CommandEntry("/agent self-host start", "start a durable self-hosting goal"),
        CommandEntry("/agent self-host status", "self-hosting goal status"),
        CommandEntry("/agent self-host watch", "watch self-hosting goal events"),
        CommandEntry("/agent self-host resume", "resume the next ready DAG node"),
        CommandEntry("/agent self-host recover", "recover restart state and continue self-hosting"),
        CommandEntry("/agent self-host next", "show next autonomous self-host action"),
        CommandEntry("/agent self-host stop", "stop a self-hosting goal"),
        CommandEntry("/agent self-host verify", "verify self-hosting goal state"),
        CommandEntry("/agent self-host promote", "promote a verified candidate JAR safely"),
        CommandEntry("/agent self-host export-evidence", "export self-host evidence bundle"),
        CommandEntry("/agent self-host history", "self-hosting goal history"),
        CommandEntry("/agent self-host learned", "learned experiences from self-hosting"),
        CommandEntry("/agent self-host benchmark", "self-hosting benchmark summary"),
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

    /**
     * Slash families [atropos.cli.CommandRouter] accepts but that this registry
     * deliberately does not advertise, because invoking them cannot do the
     * thing their name implies.
     *
     * `/swarm` is routed only to `renderError("swarm endpoint is not bound")`.
     * Listing it in the palette, in tab-completion or in `/help` would offer
     * the operator an action that does nothing, so it is named here rather
     * than silently omitted: the parity guard reads this set, which forces the
     * exemption to be deleted on the day the endpoint is actually bound.
     */
    val unboundFamilies: Set<String> = setOf("/swarm")

    fun commands(): List<String> =
        entries.map { it.command }

    /** The leading `/word` of every registered command, e.g. `/project`. */
    fun families(): Set<String> =
        entries.map { it.command.substringBefore(' ') }.toSet()

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
