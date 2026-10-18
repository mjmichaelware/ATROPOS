/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry

/**
 * ATOM-TAX-012: 4-Tier Trie Command Taxonomy & Modal Cheatsheet.
 *
 * Implements a strict 4-tier trie taxonomy:
 * Tier 1: Domain (repo, factory, agent, memory, governance, toolchain, system)
 * Tier 2: Action
 * Tier 3: Target (<pattern>, <hash>, etc.)
 * Tier 4: Leaf resolution with deterministic explanation string.
 */
data class TaxonomyNode(
    val token: String,
    val description: String,
    val children: MutableMap<String, TaxonomyNode> = mutableMapOf(),
    val isLeaf: Boolean = false,
    val leafResolution: String? = null
)

class HierarchicalCommandTaxonomy {
    val root: MutableMap<String, TaxonomyNode> = mutableMapOf()

    init {
        bootstrapDefaultTaxonomy()
    }

    fun register(domain: String, action: String, target: String, leafResolution: String) {
        val domainNode = root.getOrPut(domain) {
            TaxonomyNode(domain, "Domain: $domain")
        }
        val actionNode = domainNode.children.getOrPut(action) {
            TaxonomyNode(action, "Action: $action")
        }
        actionNode.children[target] = TaxonomyNode(
            token = target,
            description = "Target: $target",
            isLeaf = true,
            leafResolution = leafResolution
        )
    }

    fun stepInto(path: List<String>): List<TaxonomyNode> {
        if (path.isEmpty()) return root.values.toList()
        var currentMap: Map<String, TaxonomyNode> = root
        var currentNode: TaxonomyNode? = null
        for (token in path) {
            val matching = currentMap[token] ?: return emptyList()
            currentNode = matching
            currentMap = matching.children
        }
        return if (currentNode?.isLeaf == true) {
            listOf(currentNode)
        } else {
            currentMap.values.toList()
        }
    }

    fun resolveLeaf(path: List<String>): String? {
        if (path.size < 3) return null
        val domain = root[path[0]] ?: return null
        val action = domain.children[path[1]] ?: return null
        val target = action.children[path[2]] ?: action.children["<hash>"] ?: action.children["<target>"] ?: action.children.values.firstOrNull { it.isLeaf }
        return target?.leafResolution
    }

    fun cheatsheet(): String = buildString {
        appendLine("╔════════════════════════════════════════════════════════════════════════════╗")
        appendLine("║ ATROPOS 4-TIER COMMAND TAXONOMY CHEATSHEET                                 ║")
        appendLine("╠════════════════════════════════════════════════════════════════════════════╣")
        root.forEach { (domain, dNode) ->
            appendLine("║ DOMAIN: /${domain.padEnd(73)}║")
            dNode.children.forEach { (action, aNode) ->
                val targets = aNode.children.keys.joinToString(", ")
                appendLine("║   -> $action [$targets]".padEnd(77) + "║")
            }
        }
        appendLine("╚════════════════════════════════════════════════════════════════════════════╝")
    }

    private fun bootstrapDefaultTaxonomy() {
        // Domain 1: agent
        register("agent", "lifecycle", "start", "Spawns autonomous daemon run with isolated CAS branch")
        register("agent", "lifecycle", "stop", "Halts running execution loop and cleans worktrees")
        register("agent", "lifecycle", "pause", "Freezes execution state to CAS")
        register("agent", "lifecycle", "resume", "Restores loop from CAS token without re-evaluation")
        register("agent", "dispatch", "ask", "Queries provider with context")
        register("agent", "dispatch", "patch", "Generates atomic unified diff")
        register("agent", "dispatch", "repair", "Executes bounded AST repair via Tree-Sitter grammar")
        register("agent", "dispatch", "architect", "Activates 2-model planner gate: plan vs verify")
        register("agent", "dispatch", "batch", "Schedules parallel wave lanes with disjoint locks")
        register("agent", "dispatch", "simplify", "Refactors AST for complexity reduction")
        register("agent", "dispatch", "goal", "Sets long-horizon target")
        register("agent", "dispatch", "plan", "Read-only plan mode")
        register("agent", "dispatch", "compact", "LLM context reduction")

        // Domain 2: repo
        register("repo", "inspection", "status", "Verifies SSD against CAS root and reports untracked files")
        register("repo", "inspection", "diff", "Shows syntax-colored hunks with authority attribution")
        register("repo", "inspection", "log", "Sequential commit provenance with SHA-256 signatures")
        register("repo", "inspection", "map", "Semantic repository map")
        register("repo", "mutation", "init", "Initializes local git tree with ATROPOS verification hooks")
        register("repo", "mutation", "commit", "Authoritative CAS write gate requiring deterministic receipt")
        register("repo", "mutation", "rollback", "Reverts tree to target CAS hash")
        register("repo", "mutation", "stage", "Stages files to write boundary")

        // Domain 3: memory
        register("memory", "inspection", "search", "Vector & lexical recall query over episodic store")
        register("memory", "inspection", "stats", "Displays active memory bounds and token consumption")
        register("memory", "maintenance", "compact", "Deduplicates stored episodes via sentence embeddings")
        register("memory", "maintenance", "prune", "Removes expired working context past retention TTL")
        register("memory", "governance", "dloi", "Queries DLOI index lattice for grounded fact retrieval")
        register("memory", "governance", "learn", "Commits verified heuristic to durable knowledge base")

        // Domain 4: governance
        register("governance", "policy", "audit", "Scans secrets and territories")
        register("governance", "policy", "territory", "Verifies prefix boundaries against domain locks")
        register("governance", "policy", "hr", "Inspects cross-boundary risk before message delivery")
        register("governance", "policy", "paid", "Checks emergency unlock budget and token velocity")
        register("governance", "lifecycle", "custodian", "Sweeps compiler temp artifacts and orphaned sockets")
        register("governance", "lifecycle", "amend", "Applies formal authority rule amendment")

        // Domain 5: toolchain
        register("toolchain", "local", "doctor", "Audits compiler environments and credentials")
        register("toolchain", "local", "test", "Runs local test suite runner with timeout bounding")
        register("toolchain", "local", "lint", "Executes syntax and AST linters emitting diagnostic vectors")
        register("toolchain", "integrations", "mcp", "Audits active MCP server states and tool budgets")
        register("toolchain", "integrations", "sentry", "Ingests crash frame diagnostics")

        // Domain 6: session
        register("session", "context", "clear", "Purges context buffer resetting prompt scratchpad")
        register("session", "context", "compact", "Executes LLM context reduction")
        register("session", "context", "export", "Dumps transcript to file/json")
        register("session", "context", "history", "Traverses turn execution tree with branching timeline")
        register("session", "io", "copy", "Yanks response to clipboard")
        register("session", "io", "paste", "Injects clipboard into composer")
        register("session", "io", "voice", "Records speech-to-text input")
        register("session", "io", "teleport", "Binds terminal to web socket on port 8787")
        register("session", "io", "keybinding", "Registers user runtime macros")

        // Domain 7: system
        register("system", "environment", "config", "Live parameter inspector/setter in atropos.config.json")
        register("system", "environment", "theme", "Selects terminal ANSI palette")
        register("system", "environment", "verbosity", "Adjusts L1/L2/L3 output trace")
        register("system", "environment", "animation", "Sets Animation_Speed instant|fast|medium|off")
        register("system", "shell", "grep", "Executes bounded regex search")
        register("system", "shell", "alias", "Registers user runtime macros")
        register("system", "shell", "clear", "Emits ANSI screen clear sequence")
        register("system", "shell", "exit", "Terminates CLI process cleanly")
        register("system", "cost", "$", "Token usage provider limits peek")
        register("system", "interrupt", "+", "Three-level queue|interrupt|context-shift")
        register("system", "shell", "!", "Policy-gated shell inject")
    }
}
