/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.ui.TerminalText
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.Role

/**
 * ATOM-HELP-018 / ATOM 6.4: Command Documentation Leaf & Progressive Disclosure Registry.
 *
 * Implements Tier 4 progressive disclosure for all commands:
 * Collapsed: Brief one-liner + key hint.
 * Expanded (Arrow Right): Full syntax usage, practical examples matching Android app details,
 * side-effects, related commands, security/risk classification, evidence impact, cost class,
 * and required security capabilities.
 *
 * Research Directive: Terminal Markdown layout wrapping algorithms that dynamically preserve
 * code block margins and table alignment on narrow screen terminals utilize column-width-aware
 * tokenizers that break only at whitespace boundaries outside fenced verbatim blocks.
 */
data class CommandLeafDocumentation(
    val command: String,
    val brief: String,
    val syntax: String,
    val examples: List<String> = emptyList(),
    val sideEffects: String = "None (read-only query)",
    val related: List<String> = emptyList(),
    val securityRisk: String = "L0 (Standard user operation)",
    val evidenceImpact: String = "Tamper-evident verification receipt emitted to cas_evidence.jsonl",
    val costClass: String = "FREE_LOCAL",
    val requiredCapabilities: List<String> = listOf("CAP_READ")
)

object CommandLeafRegistry {
    private val leaves = mutableMapOf<String, CommandLeafDocumentation>()

    fun cardRenderer(theme: TerminalTheme): CommandSpecificationCardRenderer =
        CommandSpecificationCardRenderer(theme)

    init {
        bootstrapLeaves()
    }

    fun register(leaf: CommandLeafDocumentation) {
        leaves[leaf.command.trim().lowercase()] = leaf
    }

    fun get(command: String): CommandLeafDocumentation? =
        leaves[command.trim().lowercase()]

    fun getOrDefault(command: String, defaultBrief: String = "No description"): CommandLeafDocumentation {
        val clean = command.trim().lowercase()
        return leaves[clean] ?: CommandLeafDocumentation(
            command = command,
            brief = defaultBrief,
            syntax = command
        )
    }

    fun render(
        leaf: CommandLeafDocumentation,
        isExpanded: Boolean,
        theme: TerminalTheme,
        width: Int
    ): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val lines = mutableListOf<String>()

        val title = theme.paint(Role.ACCENT_FOCUS, leaf.command) + " — " + theme.paint(Role.TEXT_PRIMARY, leaf.brief)
        lines.add(TerminalText.ellipsize("  $title", safeWidth))

        if (!isExpanded) {
            val hint = theme.paint(Role.TEXT_MUTED, "  [Press Arrow Right to expand full documentation]")
            lines.add(hint)
            return lines
        }

        val indent = "    "
        lines.add(theme.paint(Role.TEXT_MUTED, "  " + "─".repeat((safeWidth - 4).coerceAtLeast(10))))
        lines.add(theme.paint(Role.BRAND, "  SYNTAX:") + " " + theme.paint(Role.CODE, leaf.syntax))

        if (leaf.examples.isNotEmpty()) {
            lines.add(theme.paint(Role.BRAND, "  EXAMPLES:"))
            leaf.examples.forEach { ex ->
                lines.add("$indent${theme.paint(Role.TEXT_PRIMARY, ex)}")
            }
        }

        lines.add(theme.paint(Role.BRAND, "  SIDE-EFFECTS:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.sideEffects))

        if (leaf.related.isNotEmpty()) {
            val rel = leaf.related.joinToString(", ") { theme.paint(Role.ACCENT_FOCUS, it) }
            lines.add(theme.paint(Role.BRAND, "  RELATED:") + " $rel")
        }

        lines.add(theme.paint(Role.BRAND, "  SECURITY RISK:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.securityRisk))
        lines.add(theme.paint(Role.BRAND, "  EVIDENCE IMPACT:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.evidenceImpact))
        lines.add(theme.paint(Role.BRAND, "  COST CLASS:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.costClass))
        lines.add(theme.paint(Role.BRAND, "  REQUIRED CAPABILITIES:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.requiredCapabilities.joinToString(", ")))
        lines.add(theme.paint(Role.TEXT_MUTED, "  " + "─".repeat((safeWidth - 4).coerceAtLeast(10))))

        return lines
    }

    private fun bootstrapLeaves() {
        // Core Help & System
        register(
            CommandLeafDocumentation(
                command = "/help",
                brief = "Display command catalog and interactive cheat sheet",
                syntax = "/help [category|command]",
                examples = listOf("/help", "/help agent", "/help /self-host"),
                sideEffects = "None (read-only render)",
                related = listOf("/shortcuts", "/doctor", "/status"),
                securityRisk = "L0 (Safe)",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/status",
                brief = "Inspect real-time engine, workspace, and model cascade status",
                syntax = "/status [adapters|assets|endpoints|failures|quota|route]",
                examples = listOf("/status", "/status route --full", "/status quota"),
                sideEffects = "None (read-only query)",
                related = listOf("/doctor", "/providers", "/health"),
                securityRisk = "L0 (Safe)",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/flash",
                brief = "Switch active neural cascade to fast low-latency models",
                syntax = "/flash [status]",
                examples = listOf("/flash", "/flash status"),
                sideEffects = "Switches provider routing priority to low-latency models",
                related = listOf("/model", "/providers", "/status route"),
                securityRisk = "L1 (Configuration change)",
                costClass = "FREE_OR_TIERED"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/self-host",
                brief = "Execute autonomous Phase 11 self-build and verification loop",
                syntax = "/self-host [run|start|status|verify|promote] [goal]",
                examples = listOf("/self-host status", "/self-host run fix compilation", "/self-host verify"),
                sideEffects = "Mutates isolated project worktrees, runs verification, and promotes clean builds",
                related = listOf("/agent", "/verify", "/repo"),
                securityRisk = "L3 (Gated mutation and promotion)",
                costClass = "VARIABLE",
                requiredCapabilities = listOf("CAP_WRITE", "CAP_PROCESS_SPAWN")
            )
        )

        // Category 1: AGENT_EXECUTION
        register(
            CommandLeafDocumentation(
                command = "/goal",
                brief = "Launch a long-horizon autonomous task trajectory",
                syntax = "/goal <objective-text> [--priority <level>]",
                examples = listOf(
                    "/goal refactor auth controller to eliminate null pointer exceptions",
                    "/goal implement binary search tree with unit tests --priority critical"
                ),
                sideEffects = "Initializes autonomous orchestrator and executes verification-gated tool loop",
                related = listOf("/plan", "/run", "/cancel", "/resume"),
                securityRisk = "LOCAL_MUTATION",
                evidenceImpact = "Emits trajectory merkle checkpoints into event journal",
                costClass = "VARIABLE_TIERED",
                requiredCapabilities = listOf("CAP_WRITE", "CAP_PROCESS_SPAWN")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/plan",
                brief = "Generate structured multi-step DAG plan for approval",
                syntax = "/plan <task-description>",
                examples = listOf("/plan migrate database schema from v1 to v2"),
                sideEffects = "None (read-only synthesis)",
                related = listOf("/goal", "/run"),
                securityRisk = "READ_ONLY",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/run",
                brief = "Execute the currently approved plan DAG",
                syntax = "/run [--parallelism <count>]",
                examples = listOf("/run", "/run --parallelism 4"),
                sideEffects = "Executes planned mutations across isolated worktrees",
                related = listOf("/plan", "/cancel", "/resume"),
                securityRisk = "LOCAL_MUTATION",
                costClass = "VARIABLE",
                requiredCapabilities = listOf("CAP_WRITE")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/resume",
                brief = "Resume interrupted execution from persistent checkpoint",
                syntax = "/resume [checkpoint-id]",
                examples = listOf("/resume", "/resume chk_9a8f2c"),
                sideEffects = "Restores halted execution state and continues trajectory",
                related = listOf("/checkpoint", "/goal"),
                securityRisk = "LOCAL_MUTATION",
                costClass = "VARIABLE"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/checkpoint",
                brief = "Capture immutable cryptographic snapshot of state",
                syntax = "/checkpoint [label]",
                examples = listOf("/checkpoint pre-refactor-backup"),
                sideEffects = "Serializes workspace AST, git tree, and conversation state into attested CAS",
                related = listOf("/resume", "/handoff"),
                securityRisk = "LOCAL_MUTATION",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/cancel",
                brief = "Immediately abort current task and revert uncommitted mutations",
                syntax = "/cancel [--keep-changes]",
                examples = listOf("/cancel", "/cancel --keep-changes"),
                sideEffects = "Interrupts worker threads and rolls back working files to clean HEAD",
                related = listOf("/goal", "/run"),
                securityRisk = "CRITICAL_GOVERNANCE",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_INTERRUPT")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/tree",
                brief = "Inspect execution node hierarchy and parent-child links",
                syntax = "/tree [--depth <n>]",
                examples = listOf("/tree", "/tree --depth 3"),
                sideEffects = "None (read-only query)",
                related = listOf("/queue", "/worker"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/queue",
                brief = "List and manage durable background task queue",
                syntax = "/queue [list|show|clear]",
                examples = listOf("/queue", "/queue show 1"),
                sideEffects = "Modifies durable queue when clearing or reordering",
                related = listOf("/worker", "/tree"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/worker",
                brief = "Configure autonomous parallel worker threads",
                syntax = "/worker [status|scale <n>]",
                examples = listOf("/worker status", "/worker scale 2"),
                sideEffects = "Adjusts concurrent worker thread count",
                related = listOf("/queue", "/run"),
                securityRisk = "LOCAL_MUTATION"
            )
        )

        // Category 2: PROVIDERS_MODELS
        register(
            CommandLeafDocumentation(
                command = "/model",
                brief = "Select or inspect active frontier neural model",
                syntax = "/model [name]",
                examples = listOf("/model", "/model gemini-2.5-pro"),
                sideEffects = "Updates model selection in active session",
                related = listOf("/cascade", "/providers"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/cascade",
                brief = "Configure fallback cascade order between frontier and free tiers",
                syntax = "/cascade [fast|smart|code]",
                examples = listOf("/cascade fast", "/cascade smart"),
                sideEffects = "Changes routing cascade priority",
                related = listOf("/model", "/quota"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/quota",
                brief = "Display live token quota consumption and cost ledger",
                syntax = "/quota",
                examples = listOf("/quota"),
                sideEffects = "None (read-only telemetry)",
                related = listOf("/providers", "/stats"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/providers",
                brief = "List all configured inference providers and status",
                syntax = "/providers [list|matrix|test]",
                examples = listOf("/providers", "/providers test"),
                sideEffects = "None (read-only query)",
                related = listOf("/providers test", "/providers prefer"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/providers test",
                brief = "Run live connectivity and latency probe against providers",
                syntax = "/providers test [provider-id]",
                examples = listOf("/providers test groq", "/providers test all"),
                sideEffects = "Sends minimal test ping to provider endpoint",
                related = listOf("/providers", "/doctor"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/providers enable",
                brief = "Enable previously disabled provider adapter",
                syntax = "/providers enable <id>",
                examples = listOf("/providers enable groq"),
                sideEffects = "Enables provider in local config",
                related = listOf("/providers disable", "/providers prefer"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/providers prefer",
                brief = "Set primary provider for autonomous dispatch",
                syntax = "/providers prefer <id>",
                examples = listOf("/providers prefer gemini"),
                sideEffects = "Elevates provider to tier 1 priority",
                related = listOf("/providers", "/cascade"),
                securityRisk = "LOCAL_MUTATION"
            )
        )

        // Category 3: CODE_AST_HEAL
        register(
            CommandLeafDocumentation(
                command = "/heal",
                brief = "Execute automated compiler-directed AST self-repair loop",
                syntax = "/heal <file>",
                examples = listOf("/heal src/main/App.kt"),
                sideEffects = "Applies syntax-preserving AST fixes to target file",
                related = listOf("/diagnose", "/ast", "/refactor"),
                securityRisk = "LOCAL_MUTATION",
                requiredCapabilities = listOf("CAP_WRITE")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/ast",
                brief = "Inspect parsed Tree-Sitter AST for current buffer or file",
                syntax = "/ast <file>",
                examples = listOf("/ast src/main/App.kt"),
                sideEffects = "None (read-only parse)",
                related = listOf("/symbols", "/heal"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/diagnose",
                brief = "Run syntax, lint, and typecheck diagnostics",
                syntax = "/diagnose [file]",
                examples = listOf("/diagnose", "/diagnose src/main/App.kt"),
                sideEffects = "None (read-only analysis)",
                related = listOf("/heal", "/types"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/symbols",
                brief = "Search workspace symbol table and dependency lattice",
                syntax = "/symbols [query]",
                examples = listOf("/symbols MainActivity"),
                sideEffects = "None (read-only query)",
                related = listOf("/ast", "/find"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/refactor",
                brief = "Execute semantic AST-preserving code refactoring",
                syntax = "/refactor <target> <goal>",
                examples = listOf("/refactor AuthController extract-token-verifier"),
                sideEffects = "Rewrites AST nodes in target code",
                related = listOf("/heal", "/format"),
                securityRisk = "LOCAL_MUTATION",
                requiredCapabilities = listOf("CAP_WRITE")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/format",
                brief = "Format source files using registered AST formatters",
                syntax = "/format [path]",
                examples = listOf("/format", "/format src/"),
                sideEffects = "Formats buffer in-place preserving cursor offsets",
                related = listOf("/heal", "/diagnose"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/types",
                brief = "Verify type signatures and inferred constraints",
                syntax = "/types <expression>",
                examples = listOf("/types user.token"),
                sideEffects = "None (read-only type check)",
                related = listOf("/symbols", "/diagnose"),
                securityRisk = "READ_ONLY"
            )
        )

        // Category 4: WORKSPACE_FILES
        register(
            CommandLeafDocumentation(
                command = "/files",
                brief = "Browse interactive workspace file tree",
                syntax = "/files [path]",
                examples = listOf("/files", "/files src/"),
                sideEffects = "None (read-only query)",
                related = listOf("/cat", "/find"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/cat",
                brief = "Inspect file content with syntax coloring",
                syntax = "/cat <path>",
                examples = listOf("/cat README.md", "/cat src/main/App.kt"),
                sideEffects = "None (read-only query)",
                related = listOf("/files", "/edit"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/edit",
                brief = "Open workspace file in sovereign editor",
                syntax = "/edit <path>",
                examples = listOf("/edit src/main/App.kt"),
                sideEffects = "Loads file buffer into interactive editor",
                related = listOf("/cat", "/diff"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/diff",
                brief = "Inspect uncommitted workspace unified diff",
                syntax = "/diff [path]",
                examples = listOf("/diff", "/diff src/main/App.kt"),
                sideEffects = "None (read-only query)",
                related = listOf("/git diff", "/edit"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/territory",
                brief = "Audit territorial boundaries and exclusion rules",
                syntax = "/territory [audit]",
                examples = listOf("/territory"),
                sideEffects = "None (read-only audit)",
                related = listOf("/clean", "/files"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/clean",
                brief = "Remove ephemeral build artifacts and caches",
                syntax = "/clean",
                examples = listOf("/clean"),
                sideEffects = "Purges ephemeral files outside git index",
                related = listOf("/territory", "/files"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/find",
                brief = "Fast regex and symbol search across workspace",
                syntax = "/find <pattern>",
                examples = listOf("/find TODO", "/find class\\s+Auth"),
                sideEffects = "None (read-only search)",
                related = listOf("/symbols", "/files"),
                securityRisk = "READ_ONLY"
            )
        )

        // Category 5: GIT_VCS
        register(
            CommandLeafDocumentation(
                command = "/git status",
                brief = "Inspect git working tree and staging area",
                syntax = "/git status",
                examples = listOf("/git status"),
                sideEffects = "None (read-only status)",
                related = listOf("/git diff", "/git commit"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/git diff",
                brief = "View colored diff of staged and unstaged changes",
                syntax = "/git diff",
                examples = listOf("/git diff"),
                sideEffects = "None (read-only diff)",
                related = listOf("/diff", "/git status"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/git commit",
                brief = "Commit staged changes with signed verification",
                syntax = "/git commit -m <message>",
                examples = listOf("/git commit -m \"fix: auth error\""),
                sideEffects = "Creates git commit and generates CAS receipt",
                related = listOf("/git sync", "/git status"),
                securityRisk = "LOCAL_MUTATION",
                requiredCapabilities = listOf("CAP_WRITE")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/git branch",
                brief = "List, create, or switch git branches",
                syntax = "/git branch [name]",
                examples = listOf("/git branch", "/git branch feat/new-ui"),
                sideEffects = "Switches active git branch",
                related = listOf("/git status", "/git sync"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/git sync",
                brief = "Fetch, rebase, and push to upstream remote",
                syntax = "/git sync",
                examples = listOf("/git sync"),
                sideEffects = "Performs network fetch, rebase, and push",
                related = listOf("/git commit", "/git conflicts"),
                securityRisk = "NETWORK_EGRESS",
                requiredCapabilities = listOf("CAP_NETWORK", "CAP_WRITE")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/git conflicts",
                brief = "Analyze and resolve rebase/merge conflicts",
                syntax = "/git conflicts",
                examples = listOf("/git conflicts"),
                sideEffects = "None (read-only conflict analysis)",
                related = listOf("/git sync", "/heal"),
                securityRisk = "READ_ONLY"
            )
        )

        // Category 6: MCP_INTEGRATIONS
        register(
            CommandLeafDocumentation(
                command = "/mcp list",
                brief = "List configured Model Context Protocol servers",
                syntax = "/mcp list",
                examples = listOf("/mcp list"),
                sideEffects = "None (read-only query)",
                related = listOf("/mcp test", "/mcp policy"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/mcp test",
                brief = "Health-check connection to active MCP servers",
                syntax = "/mcp test [server]",
                examples = listOf("/mcp test", "/mcp test github"),
                sideEffects = "Pings MCP server processes",
                related = listOf("/mcp list", "/doctor"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/mcp call",
                brief = "Invoke allowlisted MCP tool with arguments",
                syntax = "/mcp call <server> <tool> [args]",
                examples = listOf("/mcp call filesystem list_directory {\"path\":\"/\"}"),
                sideEffects = "Executes external tool action in sandbox",
                related = listOf("/mcp allow", "/mcp policy"),
                securityRisk = "LOCAL_MUTATION",
                requiredCapabilities = listOf("CAP_PROCESS_SPAWN")
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/mcp search",
                brief = "Search registered tools across MCP servers",
                syntax = "/mcp search <query>",
                examples = listOf("/mcp search files", "/mcp search git"),
                sideEffects = "None (read-only query)",
                related = listOf("/mcp list", "/mcp call"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/mcp allow",
                brief = "Authorize tool capability in sandbox policy",
                syntax = "/mcp allow <server>",
                examples = listOf("/mcp allow memory", "/mcp allow filesystem"),
                sideEffects = "Updates security permission table",
                related = listOf("/mcp policy", "/mcp call"),
                securityRisk = "LOCAL_MUTATION"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/mcp policy",
                brief = "Inspect MCP sandboxing and permission rules",
                syntax = "/mcp policy",
                examples = listOf("/mcp policy"),
                sideEffects = "None (read-only query)",
                related = listOf("/mcp allow", "/mcp list"),
                securityRisk = "READ_ONLY"
            )
        )

        // Category 7: DIAGNOSTICS_HUD
        register(
            CommandLeafDocumentation(
                command = "/doctor",
                brief = "Run comprehensive backend, provider, and network diagnostics",
                syntax = "/doctor",
                examples = listOf("/doctor"),
                sideEffects = "None (read-only health audit)",
                related = listOf("/answers", "/stats", "/providers test"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/answers",
                brief = "Display Six Answers progress HUD",
                syntax = "/answers",
                examples = listOf("/answers"),
                sideEffects = "None (read-only telemetry)",
                related = listOf("/doctor", "/stats"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/activity",
                brief = "Stream live execution event activity log",
                syntax = "/activity",
                examples = listOf("/activity"),
                sideEffects = "None (read-only stream)",
                related = listOf("/journal", "/stats"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/journal",
                brief = "Query durable cryptographic event journal",
                syntax = "/journal [limit]",
                examples = listOf("/journal", "/journal 50"),
                sideEffects = "None (read-only query)",
                related = listOf("/activity", "/dopamine"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/dopamine",
                brief = "Inspect dopamine reward engine and curiosity metrics",
                syntax = "/dopamine",
                examples = listOf("/dopamine"),
                sideEffects = "None (read-only telemetry)",
                related = listOf("/stats", "/journal"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/stats",
                brief = "Inspect real-time process and memory telemetry",
                syntax = "/stats",
                examples = listOf("/stats"),
                sideEffects = "None (read-only telemetry)",
                related = listOf("/quota", "/doctor"),
                securityRisk = "READ_ONLY"
            )
        )

        // Category 8: SPECGRAPH_RESEARCH
        register(
            CommandLeafDocumentation(
                command = "/specgraph",
                brief = "Visualize project specgraph dependency DAG",
                syntax = "/specgraph",
                examples = listOf("/specgraph"),
                sideEffects = "None (read-only rendering)",
                related = listOf("/research", "/certify"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/research",
                brief = "Browse research lineages and experiment cards",
                syntax = "/research [topic]",
                examples = listOf("/research active inference", "/research mcp"),
                sideEffects = "None (read-only query)",
                related = listOf("/specgraph", "/handoff"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/handoff",
                brief = "Generate clean context handoff envelope",
                syntax = "/handoff",
                examples = listOf("/handoff"),
                sideEffects = "Compiles serialized context state envelope",
                related = listOf("/checkpoint", "/certify"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/certify",
                brief = "Attest Merkle proof for current workspace state",
                syntax = "/certify",
                examples = listOf("/certify"),
                sideEffects = "Emits cryptographically signed Merkle certificate",
                related = listOf("/release", "/specgraph"),
                securityRisk = "READ_ONLY"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/release",
                brief = "Prepare and sign formal release artifact",
                syntax = "/release",
                examples = listOf("/release"),
                sideEffects = "Generates release bundles, updates changelog, and creates git tag",
                related = listOf("/certify", "/handoff"),
                securityRisk = "CRITICAL_GOVERNANCE",
                requiredCapabilities = listOf("CAP_WRITE", "CAP_PROCESS_SPAWN")
            )
        )
    }
}
