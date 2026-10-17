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
        // Domain 1: repo
        register("repo", "rollback", "<hash>", "Rolls back repository workspace to commit <hash> with CAS baseline invalidation")
        register("repo", "status", "workspace", "Displays working tree state and uncommitted file modifications")
        register("repo", "diff", "staged", "Computes AST-level diff against authoritative content-addressed storage")
        register("repo", "commit", "<message>", "Records deterministic immutable tree commit into CAS receipt ledger")

        // Domain 2: factory
        register("factory", "generate", "<spec>", "Synthesizes multi-tier application architecture adhering to formal contracts")
        register("factory", "repair", "<error>", "Invokes Sentry repair coordinator to synthesize corrective mutations")
        register("factory", "resume", "<run_id>", "Restores speculative synthesis wave from persistent event journal")
        register("factory", "audit", "loc", "Enforces strict 500-LOC ceiling across all synthesized source files")

        // Domain 3: agent
        register("agent", "run", "<goal>", "Launches autonomous multi-turn goal execution within attenuated sandbox")
        register("agent", "status", "active", "Queries live execution status and active concurrency lane metrics")
        register("agent", "cancel", "<task_id>", "Terminates active synthesis lane and purges speculative workspaces")

        // Domain 4: memory
        register("memory", "query", "<key>", "Retrieves stored AST symbols and learned conflict lemmas from CAS")
        register("memory", "prune", "stale", "Removes unreferenced ephemeral hypothesis nodes from constraint store")

        // Domain 5: governance
        register("governance", "attest", "agents.md", "Validates cryptographic root signature of authoritative AGENTS.md ledger")
        register("governance", "verify", "cas", "Calculates zero-entropy Merkle root over physical workspace bytes")

        // Domain 6: toolchain
        register("toolchain", "detect", "native", "Scans system PATH via PlatformAdapterRegistry for portable compilers")
        register("toolchain", "doctor", "env", "Verifies cross-platform toolchain isolation and memory limits")

        // Domain 7: system (including missing grep, undo, alias, history, config, clear)
        register("system", "grep", "<pattern>", "Searches indexed repository content using bounded regex pattern")
        register("system", "undo", "<step>", "Reverts the last speculative workspace mutation step")
        register("system", "alias", "<name>", "Defines or inspects user command shortcut aliases")
        register("system", "history", "<limit>", "Displays chronological command and execution history")
        register("system", "config", "<key>", "Inspects or updates runtime configuration key-value store")
        register("system", "clear", "screen", "Clears active viewport buffer and resets cursor position")
    }
}
