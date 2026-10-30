/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry

/**
 * ATOM-TAX-012 / ATOM 6.3: 4-Tier Trie Command Taxonomy & Modal Cheatsheet.
 *
 * Implements a strict 4-tier trie taxonomy:
 * Tier 1: Domain (repo, factory, agent, memory, governance, toolchain, system)
 * Tier 2: Action
 * Tier 3: Target (<pattern>, <hash>, etc.)
 * Tier 4: Leaf resolution with deterministic explanation string.
 *
 * Interactive arrow-key navigation semantics:
 * - Right-arrow and Enter descend into child categories.
 * - Left-arrow ascends to parent nodes.
 * - Escape dismisses the hierarchy cleanly.
 *
 * Research Directive: Compact prefix trie data compression structures (such as succinct
 * LOUDS or radix trie layouts) enable sub-millisecond symbol traversal and zero heap overhead
 * across thousands of command definitions on resource-constrained mobile JVM runtimes.
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

    fun canDescend(path: List<String>): Boolean {
        val nodes = stepInto(path)
        return nodes.isNotEmpty() && nodes.any { !it.isLeaf }
    }

    fun ascend(path: List<String>): List<String> =
        if (path.isNotEmpty()) path.dropLast(1) else emptyList()

    fun descend(path: List<String>, selection: String): List<String> =
        path + selection

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
        register("repo", "workspace", "init", "Initializes isolated target workspace with axiomatic isolation")
        register("repo", "workspace", "status", "Inspects worktree state and dirty fingerprint")
        register("repo", "workspace", "verify", "Executes full multi-surface parity verification")
        register("repo", "workspace", "diff", "Renders unified diff from baseline commit")

        // Domain 3: models
        register("models", "cascade", "flash", "Switches provider cascade to fast low-latency models")
        register("models", "cascade", "status", "Renders provider adapter activation matrix")
        register("models", "cascade", "verify", "Verifies provider connectivity and token exchange")
    }
}
