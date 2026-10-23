/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.command

/**
 * Authoritative registry of all Atropos system commands.
 * Populates the interactive `/` category launcher, auto-complete palette, and detailed help sheets.
 */
object CommandCatalogRegistry {

    val allCommands: List<AtroposCommandSpec> = listOf(
        // Category 1: AGENT_EXECUTION (Autonomous Agents & Goals)
        AtroposCommandSpec(
            name = "/goal",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Trajectory & Work",
            synopsis = "Launch a long-horizon autonomous task trajectory",
            proseDescription = "Submits a sovereign objective to the AutonomousOrchestrator. The engine initializes a LongHorizonTrajectoryEngine instance, verifies workspace territory boundaries, synthesizes plan steps, and will not stop until verified complete or explicit human preemption.",
            usage = "/goal <objective-text> [--priority <level>]",
            arguments = listOf(
                CommandArgSpec("objective", "Natural language specification of the software goal", required = true)
            ),
            flags = listOf(
                CommandFlagSpec("--priority", "-p", "Scheduling priority (low, normal, critical)", takesValue = true),
                CommandFlagSpec("--dry-run", null, "Synthesize trajectory plan without executing file writes")
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/goal refactor auth controller to eliminate null pointer exceptions",
                "/goal implement binary search tree with unit tests --priority critical"
            )
        ),
        AtroposCommandSpec(
            name = "/plan",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Trajectory & Work",
            synopsis = "Generate structured multi-step DAG plan for approval",
            proseDescription = "Synthesizes an execution DAG without performing mutations. Displays affected file territories, required provider models, risk analysis, and estimated token quota before committing to work.",
            usage = "/plan <task-description>",
            arguments = listOf(
                CommandArgSpec("task", "Goal description to analyze and decompose", required = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf(
                "/plan migrate database schema from v1 to v2"
            )
        ),
        AtroposCommandSpec(
            name = "/run",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Trajectory & Work",
            synopsis = "Execute the currently approved plan DAG",
            proseDescription = "Triggers DagExecutionService to execute wave batches in parallel across isolated worktrees. Synchronizes progress telemetry to the Six Answers HUD.",
            usage = "/run [--parallelism <count>]",
            flags = listOf(
                CommandFlagSpec("--parallelism", "-j", "Max parallel worker threads", takesValue = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/run",
                "/run --parallelism 4"
            )
        ),
        AtroposCommandSpec(
            name = "/resume",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Trajectory & Work",
            synopsis = "Resume interrupted execution from persistent checkpoint",
            proseDescription = "Loads the last verified execution checkpoint from EventJournalService and resumes active trajectory from the exact node where it was halted.",
            usage = "/resume [checkpoint-id]",
            arguments = listOf(
                CommandArgSpec("checkpoint-id", "Optional checkpoint hash. If omitted, uses latest checkpoint", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/resume",
                "/resume chk_9a8f2c"
            )
        ),
        AtroposCommandSpec(
            name = "/checkpoint",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Queue & Hierarchy",
            synopsis = "Capture immutable cryptographic snapshot of state",
            proseDescription = "Forces immediate serialization of current workspace AST, git tree, and conversation state into an attested checkpoint envelope.",
            usage = "/checkpoint [label]",
            arguments = listOf(
                CommandArgSpec("label", "Descriptive tag for the checkpoint", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/checkpoint pre-refactor-backup"
            )
        ),
        AtroposCommandSpec(
            name = "/cancel",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Queue & Hierarchy",
            synopsis = "Immediately abort current task and revert uncommitted mutations",
            proseDescription = "Signals QueueInterruptCoordinator to forcibly interrupt active worker threads, revoke pending tool executions, and reset working files to clean git HEAD.",
            usage = "/cancel [--keep-changes]",
            flags = listOf(
                CommandFlagSpec("--keep-changes", "-k", "Halt execution without reverting modified workspace files")
            ),
            safetyTier = SafetyTier.CRITICAL_GOVERNANCE,
            exampleUsages = listOf(
                "/cancel",
                "/cancel --keep-changes"
            )
        ),
        AtroposCommandSpec(
            name = "/tree",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Queue & Hierarchy",
            synopsis = "Display active DAG dependency lattice and execution waves",
            proseDescription = "Renders an ASCII or structured tree view of all task nodes in the current goal graph, highlighting completed, running, blocked, and failed nodes.",
            usage = "/tree [--filter <state>]",
            flags = listOf(
                CommandFlagSpec("--filter", "-f", "Filter by state: pending, active, completed, failed", takesValue = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf(
                "/tree",
                "/tree --filter active"
            )
        ),
        AtroposCommandSpec(
            name = "/queue",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Queue & Hierarchy",
            synopsis = "Inspect and manipulate the background agent task queue",
            proseDescription = "Lists all pending, blocked, and running background jobs. Allows prioritizing tasks or deleting orphaned work items.",
            usage = "/queue [list | clear | remove <id>]",
            arguments = listOf(
                CommandArgSpec("subcommand", "Queue action: list, clear, or remove", required = false, defaultValue = "list")
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/queue list",
                "/queue remove task-44"
            )
        ),
        AtroposCommandSpec(
            name = "/worker",
            category = CommandCategory.AGENT_EXECUTION,
            subcategory = "Queue & Hierarchy",
            synopsis = "Inspect active hierarchical worker agent pool",
            proseDescription = "Displays the status, provider assignment, current AST node, and token spend of all child workers operating under the supervisor.",
            usage = "/worker [list | status <worker-id>]",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf(
                "/worker list"
            )
        ),

        // Category 2: PROVIDERS_MODELS (Providers & Neural Cascades)
        AtroposCommandSpec(
            name = "/providers",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "List all configured model providers and live health status",
            proseDescription = "Queries ProviderOnboardingService and StaticProviderDescriptorRegistry to list all supported neural providers (Groq, Gemini, DeepSeek, Bedrock, Ollama, Frontier Alpha, Frontier Omega), showing credential availability, latency, and operational health.",
            usage = "/providers",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/providers")
        ),
        AtroposCommandSpec(
            name = "/providers test",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "Execute live lightweight probe against specified provider",
            proseDescription = "Sends a zero-token or minimal probe request to verify network connectivity, authentication validity, and rate limit headroom for a target provider.",
            usage = "/providers test <provider-id>",
            arguments = listOf(
                CommandArgSpec("provider-id", "Target provider (e.g. gemini, groq, deepseek, ollama)", required = true)
            ),
            safetyTier = SafetyTier.NETWORK_EGRESS,
            exampleUsages = listOf(
                "/providers test gemini",
                "/providers test groq"
            )
        ),
        AtroposCommandSpec(
            name = "/providers enable",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "Enable a provider for automatic cascade routing",
            proseDescription = "Marks a healthy provider as eligible for participation in dynamic issue routing and multi-tier cascades.",
            usage = "/providers enable <provider-id>",
            arguments = listOf(
                CommandArgSpec("provider-id", "Provider to enable", required = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/providers enable groq")
        ),
        AtroposCommandSpec(
            name = "/providers prefer",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "Set prioritized primary provider for autonomous execution",
            proseDescription = "Reorders the provider cascade hierarchy to route initial hypothesis and synthesis tasks to this provider first.",
            usage = "/providers prefer <provider-id>",
            arguments = listOf(
                CommandArgSpec("provider-id", "Preferred provider identifier", required = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/providers prefer gemini")
        ),
        AtroposCommandSpec(
            name = "/model",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "Inspect or override current active model for session",
            proseDescription = "Allows overriding the dynamically selected model for the current interactive conversation or worker lane.",
            usage = "/model [model-identifier]",
            arguments = listOf(
                CommandArgSpec("model-identifier", "Model name (e.g. sovereign-v1, frontier-fast, qwen2.5-coder)", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/model",
                "/model sovereign-v1"
            )
        ),
        AtroposCommandSpec(
            name = "/cascade",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "View and configure multi-tier cost/performance cascade",
            proseDescription = "Inspects the Free-to-Paid fallback ladder: Free/Local -> Mid-tier -> Frontier Tier.",
            usage = "/cascade [--mode <auto|local-only|paid-unlocked>]",
            flags = listOf(
                CommandFlagSpec("--mode", "-m", "Operating cascade policy mode", takesValue = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/cascade",
                "/cascade --mode local-only"
            )
        ),
        AtroposCommandSpec(
            name = "/quota",
            category = CommandCategory.PROVIDERS_MODELS,
            synopsis = "Display token consumption, cost, and rate-limit headroom",
            proseDescription = "Reads the QuotaLedger to show prompt tokens, completion tokens, cached tokens, and estimated monetary cost across all providers in current billing cycle.",
            usage = "/quota [--reset]",
            flags = listOf(
                CommandFlagSpec("--reset", null, "Reset current local session quota counters")
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/quota")
        ),

        // Category 3: CODE_AST_HEAL (AST Parsing & Self-Healing)
        AtroposCommandSpec(
            name = "/heal",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Trigger AutonomousAstHealingLoop on files with compiler errors",
            proseDescription = "Inspects compiler diagnostics, extracts affected AST symbol subtrees, synthesizes candidate repairs, and verifies each against the compiler until zero diagnostics remain.",
            usage = "/heal [target-file-path]",
            arguments = listOf(
                CommandArgSpec("file", "Specific source file to heal. If omitted, scans entire worktree", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf(
                "/heal",
                "/heal src/main/kotlin/MyClass.kt"
            )
        ),
        AtroposCommandSpec(
            name = "/ast",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Parse and inspect AST node tree for a file or symbol",
            proseDescription = "Produces a structured AST representation of functions, classes, imports, and variables with token spans and type signatures.",
            usage = "/ast <file-path> [--symbol <name>]",
            arguments = listOf(
                CommandArgSpec("file", "Target source file path", required = true)
            ),
            flags = listOf(
                CommandFlagSpec("--symbol", "-s", "Specific symbol to inspect", takesValue = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf(
                "/ast src/core/Router.kt",
                "/ast src/core/Router.kt --symbol dispatch"
            )
        ),
        AtroposCommandSpec(
            name = "/diagnose",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Run language-server diagnostics across active workspace",
            proseDescription = "Executes non-mutating compiler checks to surface syntax errors, missing imports, type mismatches, and deprecation warnings.",
            usage = "/diagnose [--severity <error|warning>]",
            flags = listOf(
                CommandFlagSpec("--severity", null, "Minimum severity to display (error, warning, info)", takesValue = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/diagnose")
        ),
        AtroposCommandSpec(
            name = "/symbols",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Search AST symbol table across the entire project",
            proseDescription = "Searches the global symbol index by name or signature to identify definitions, callers, and interface implementations.",
            usage = "/symbols <query>",
            arguments = listOf(
                CommandArgSpec("query", "Symbol name or substring", required = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/symbols ProviderOnboarding")
        ),
        AtroposCommandSpec(
            name = "/refactor",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Perform structured semantic rename or extraction",
            proseDescription = "Performs safe multi-file refactoring with atomic AST verification across all call sites.",
            usage = "/refactor rename <old-symbol> <new-symbol>",
            arguments = listOf(
                CommandArgSpec("action", "Refactor action: rename, extract-method, inline", required = true),
                CommandArgSpec("target", "Target symbol name", required = true),
                CommandArgSpec("replacement", "New symbol name or destination", required = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/refactor rename oldMethod newMethod")
        ),
        AtroposCommandSpec(
            name = "/format",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Format source code according to project style contracts",
            proseDescription = "Runs deterministic formatting on target files without altering semantics.",
            usage = "/format [path]",
            arguments = listOf(
                CommandArgSpec("path", "File or directory to format", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/format src/")
        ),
        AtroposCommandSpec(
            name = "/types",
            category = CommandCategory.CODE_AST_HEAL,
            synopsis = "Check static type constraints across package boundaries",
            proseDescription = "Validates contract boundary parity between Kotlin multiplatform core and TypeScript front-end contracts.",
            usage = "/types [--strict]",
            flags = listOf(
                CommandFlagSpec("--strict", null, "Fail on any loose any or unchecked cast")
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/types --strict")
        ),

        // Category 4: WORKSPACE_FILES (Workspace, Territory & Files)
        AtroposCommandSpec(
            name = "/files",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "List and navigate workspace file tree",
            proseDescription = "Returns the structured directory tree within the allowed territory boundaries, respecting gitignore and hidden file policies.",
            usage = "/files [directory-path]",
            arguments = listOf(
                CommandArgSpec("path", "Subdirectory to explore", required = false, defaultValue = ".")
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/files", "/files src/main")
        ),
        AtroposCommandSpec(
            name = "/cat",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Read and print file contents with line numbers",
            proseDescription = "Displays file contents up to safety limit (512 KB), with optional line range slicing.",
            usage = "/cat <file-path> [--lines <start-end>]",
            arguments = listOf(
                CommandArgSpec("file", "Path to file", required = true)
            ),
            flags = listOf(
                CommandFlagSpec("--lines", "-l", "Line range (e.g. 10-50)", takesValue = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/cat build.gradle.kts", "/cat src/Main.kt --lines 1-40")
        ),
        AtroposCommandSpec(
            name = "/edit",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Perform atomic find-and-replace edit on workspace file",
            proseDescription = "Applies a bounded contiguous string replacement to a file, enforcing territory boundary and atomic write invariants.",
            usage = "/edit <file-path>",
            arguments = listOf(
                CommandArgSpec("file", "Path to file to edit", required = true)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/edit src/Config.kt")
        ),
        AtroposCommandSpec(
            name = "/diff",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Compute unified diff between working tree and git HEAD",
            proseDescription = "Calculates colored unified diff showing modified, added, and deleted lines.",
            usage = "/diff [file-path]",
            arguments = listOf(
                CommandArgSpec("file", "Specific file diff, or entire tree if omitted", required = false)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/diff", "/diff src/App.kt")
        ),
        AtroposCommandSpec(
            name = "/territory",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Inspect declared territory boundaries and path rules",
            proseDescription = "Displays allowed writable paths, read-only areas, and forbidden system directories under sovereign boundary laws.",
            usage = "/territory",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/territory")
        ),
        AtroposCommandSpec(
            name = "/clean",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Remove untracked build artifacts and temporary scratch files",
            proseDescription = "Cleans scratch directories and temporary logs without touching tracked source files.",
            usage = "/clean [--force]",
            flags = listOf(
                CommandFlagSpec("--force", "-f", "Bypass confirmation prompt")
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/clean")
        ),
        AtroposCommandSpec(
            name = "/find",
            category = CommandCategory.WORKSPACE_FILES,
            synopsis = "Fuzzy search files by filename pattern or path",
            proseDescription = "Fast path matching to locate files within the project tree.",
            usage = "/find <pattern>",
            arguments = listOf(
                CommandArgSpec("pattern", "Glob or regex pattern", required = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/find *Test.kt", "/find *.tsx")
        ),

        // Category 5: GIT_VCS (Git & Version Control)
        AtroposCommandSpec(
            name = "/git status",
            category = CommandCategory.GIT_VCS,
            synopsis = "Display working tree status and staged changes",
            proseDescription = "Invokes ShellCommandRunner gitStatus to display modified, untracked, and staged files through the bounded agency gate.",
            usage = "/git status",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/git status")
        ),
        AtroposCommandSpec(
            name = "/git diff",
            category = CommandCategory.GIT_VCS,
            synopsis = "Show uncommitted git diffs",
            proseDescription = "Outputs git diff against HEAD with line-level addition and deletion markers.",
            usage = "/git diff [path]",
            arguments = listOf(
                CommandArgSpec("path", "File path to restrict diff to", required = false)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/git diff")
        ),
        AtroposCommandSpec(
            name = "/git commit",
            category = CommandCategory.GIT_VCS,
            synopsis = "Create a verified git commit with structured message",
            proseDescription = "Stages modified files and executes git commit with mandatory sovereign attribution and progress ledger reference.",
            usage = "/git commit -m \"<message>\" [--all]",
            flags = listOf(
                CommandFlagSpec("-m", "--message", "Commit message", takesValue = true),
                CommandFlagSpec("-a", "--all", "Stage all modified tracked files")
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/git commit -m \"fix: resolve null check in router\"")
        ),
        AtroposCommandSpec(
            name = "/git branch",
            category = CommandCategory.GIT_VCS,
            synopsis = "List, create, or switch git branches",
            proseDescription = "Inspects local and remote branches or switches working branch safely.",
            usage = "/git branch [branch-name]",
            arguments = listOf(
                CommandArgSpec("branch-name", "Branch to checkout or create", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/git branch", "/git branch feature/new-ui")
        ),
        AtroposCommandSpec(
            name = "/git sync",
            category = CommandCategory.GIT_VCS,
            synopsis = "Synchronize local branch with remote repository",
            proseDescription = "Fetches upstream commits and pulls or pushes changes over the authorized transport.",
            usage = "/git sync [--push | --pull]",
            flags = listOf(
                CommandFlagSpec("--push", null, "Push commits to remote"),
                CommandFlagSpec("--pull", null, "Pull commits from remote")
            ),
            safetyTier = SafetyTier.NETWORK_EGRESS,
            exampleUsages = listOf("/git sync", "/git sync --push")
        ),
        AtroposCommandSpec(
            name = "/git conflicts",
            category = CommandCategory.GIT_VCS,
            synopsis = "Detect and display rebase/merge conflict markers",
            proseDescription = "Scans files with unresolved git merge conflicts, preparing them for AST healing resolution.",
            usage = "/git conflicts",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/git conflicts")
        ),

        // Category 6: MCP_INTEGRATIONS (MCP Servers & Tool Governance)
        AtroposCommandSpec(
            name = "/mcp list",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "List all configured MCP servers and active tool budgets",
            proseDescription = "Queries McpHostManager to inspect running stdio and HTTP Model Context Protocol servers, showing tool counts, health status, and response latency.",
            usage = "/mcp list",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/mcp list")
        ),
        AtroposCommandSpec(
            name = "/mcp test",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "Ping all MCP servers and record health attestation",
            proseDescription = "Executes initialize and tools/list handshakes on all declared servers, writing health timestamps to health.tsv.",
            usage = "/mcp test [server-name]",
            arguments = listOf(
                CommandArgSpec("server-name", "Specific server to test, or all if omitted", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/mcp test", "/mcp test filesystem")
        ),
        AtroposCommandSpec(
            name = "/mcp call",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "Directly invoke an MCP tool with json arguments",
            proseDescription = "Executes an approved MCP tool call through BoundedProcessRunner, capturing stdout/stderr and recording SHA-256 evidence.",
            usage = "/mcp call <server> <tool> [arguments-json]",
            arguments = listOf(
                CommandArgSpec("server", "MCP server name", required = true),
                CommandArgSpec("tool", "Tool function name", required = true),
                CommandArgSpec("arguments", "JSON arguments payload", required = false, defaultValue = "{}")
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/mcp call filesystem read_file {\"path\": \"README.md\"}")
        ),
        AtroposCommandSpec(
            name = "/mcp search",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "Search MCP tool descriptions and capabilities",
            proseDescription = "Queries tool descriptions across all registered servers to find available capabilities matching a keyword query.",
            usage = "/mcp search <query>",
            arguments = listOf(
                CommandArgSpec("query", "Capability or tool name search query", required = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/mcp search database", "/mcp search git")
        ),
        AtroposCommandSpec(
            name = "/mcp allow",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "Allowlist a community MCP server",
            proseDescription = "Adds an external community server to the explicit allowlist under CommunityServerPolicyGovernor.",
            usage = "/mcp allow <server-name>",
            arguments = listOf(
                CommandArgSpec("server-name", "Server name to allowlist", required = true)
            ),
            safetyTier = SafetyTier.CRITICAL_GOVERNANCE,
            exampleUsages = listOf("/mcp allow custom-docker-runner")
        ),
        AtroposCommandSpec(
            name = "/mcp policy",
            category = CommandCategory.MCP_INTEGRATIONS,
            synopsis = "Display MCP security and air-gap governance policies",
            proseDescription = "Shows tool description budgets, environment variable limits, and transport air-gap interceptor status.",
            usage = "/mcp policy",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/mcp policy")
        ),

        // Category 7: DIAGNOSTICS_HUD (Telemetry, Doctor & HUD)
        AtroposCommandSpec(
            name = "/doctor",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "Comprehensive system diagnostic and health audit",
            proseDescription = "Executes BackendDoctor to inspect JVM runtime, bridge port readiness, git state, provider credentials, MCP servers, and zero-retention compliance.",
            usage = "/doctor [--verbose]",
            flags = listOf(
                CommandFlagSpec("--verbose", "-v", "Display full configuration details and environment aliases")
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/doctor", "/doctor --verbose")
        ),
        AtroposCommandSpec(
            name = "/answers",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "Display the Six Continuous Answers state snapshot",
            proseDescription = "Outputs the current objective, current action, invariant law, changes made, verification outcome, and confidence metric.",
            usage = "/answers",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/answers")
        ),
        AtroposCommandSpec(
            name = "/activity",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "Stream real-time computational events and tool operations",
            proseDescription = "Streams execution events from BridgeEventHub, including file mutations, AST repairs, and approval decisions.",
            usage = "/activity [--limit <n>]",
            flags = listOf(
                CommandFlagSpec("--limit", "-n", "Number of recent events to display", takesValue = true)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/activity", "/activity --limit 20")
        ),
        AtroposCommandSpec(
            name = "/journal",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "Inspect append-only execution history journal",
            proseDescription = "Reads EventJournalService history store to show past run runs, tokens consumed, and duration metrics.",
            usage = "/journal [run-id]",
            arguments = listOf(
                CommandArgSpec("run-id", "Specific run ID to inspect", required = false)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/journal")
        ),
        AtroposCommandSpec(
            name = "/dopamine",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "Inspect verified reward metrics and benchmark telemetry",
            proseDescription = "Displays the Dopamine Ledger metrics: successful test pass rate, verified predicate completions, and AST healing efficiency.",
            usage = "/dopamine",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/dopamine")
        ),
        AtroposCommandSpec(
            name = "/stats",
            category = CommandCategory.DIAGNOSTICS_HUD,
            synopsis = "System performance, memory, and thread metrics",
            proseDescription = "Displays JVM memory usage, active coroutine counts, garbage collection statistics, and CPU utilization.",
            usage = "/stats",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/stats")
        ),

        // Category 8: SPECGRAPH_RESEARCH (SpecGraph & Merkle Sealing)
        AtroposCommandSpec(
            name = "/specgraph",
            category = CommandCategory.SPECGRAPH_RESEARCH,
            synopsis = "Display active SpecGraph project specification lattice",
            proseDescription = "Inspects the project's SpecGraph nodes, showing requirement statements, source bindings, research dependencies, and acceptance criteria.",
            usage = "/specgraph [project-id]",
            arguments = listOf(
                CommandArgSpec("project-id", "Target project ID, or current if omitted", required = false)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/specgraph")
        ),
        AtroposCommandSpec(
            name = "/research",
            category = CommandCategory.SPECGRAPH_RESEARCH,
            synopsis = "Inspect research documents and evidence lineage",
            proseDescription = "Reads research notes and domain source documents ingested under FactoryResearchService.",
            usage = "/research [document-id]",
            arguments = listOf(
                CommandArgSpec("document-id", "Document to view", required = false)
            ),
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/research")
        ),
        AtroposCommandSpec(
            name = "/handoff",
            category = CommandCategory.SPECGRAPH_RESEARCH,
            synopsis = "Generate exportable sovereign handoff bundle",
            proseDescription = "Compiles all project state, SpecGraph lattice, verified evidence hashes, and progress ledger entries into an archive.",
            usage = "/handoff export [output-dir]",
            arguments = listOf(
                CommandArgSpec("action", "Handoff action: export, import, inspect", required = true),
                CommandArgSpec("dir", "Target output directory", required = false)
            ),
            safetyTier = SafetyTier.LOCAL_MUTATION,
            exampleUsages = listOf("/handoff export ./exports")
        ),
        AtroposCommandSpec(
            name = "/certify",
            category = CommandCategory.SPECGRAPH_RESEARCH,
            synopsis = "Run MerkleReleaseCertifier and generate cryptographic master seal",
            proseDescription = "Verifies JAR SHA-256, Android APK SHA-256, Web Standalone SHA-256, and contract test passes, outputting an immutable Merkle root.",
            usage = "/certify [--seal]",
            flags = listOf(
                CommandFlagSpec("--seal", null, "Write certified release receipt to disk")
            ),
            safetyTier = SafetyTier.CRITICAL_GOVERNANCE,
            exampleUsages = listOf("/certify", "/certify --seal")
        ),
        AtroposCommandSpec(
            name = "/release",
            category = CommandCategory.SPECGRAPH_RESEARCH,
            synopsis = "Trigger FinalConvergenceReleaseGate verification",
            proseDescription = "Executes the final capstone convergence release gate to assert multi-surface parity across CLI, Web, and Android before publishing.",
            usage = "/release verify",
            safetyTier = SafetyTier.READ_ONLY,
            exampleUsages = listOf("/release verify")
        )
    )

    fun getByCategory(category: CommandCategory): List<AtroposCommandSpec> =
        allCommands.filter { it.category == category }

    fun getSubcategories(category: CommandCategory): List<String> =
        allCommands.filter { it.category == category && it.subcategory != null }
            .mapNotNull { it.subcategory }
            .distinct()

    fun getBySubcategory(category: CommandCategory, subcategory: String): List<AtroposCommandSpec> =
        allCommands.filter { it.category == category && it.subcategory == subcategory }

    fun find(commandName: String): AtroposCommandSpec? {
        val clean = if (commandName.startsWith("/")) commandName else "/$commandName"
        return allCommands.firstOrNull { it.name.equals(clean, ignoreCase = true) }
    }

    fun search(query: String): List<AtroposCommandSpec> {
        if (query.isBlank()) return allCommands
        val clean = query.trim().removePrefix("/").lowercase()
        return allCommands.filter {
            it.name.lowercase().contains(clean) ||
            it.synopsis.lowercase().contains(clean) ||
            it.category.title.lowercase().contains(clean) ||
            (it.subcategory?.lowercase()?.contains(clean) == true)
        }
    }
}
