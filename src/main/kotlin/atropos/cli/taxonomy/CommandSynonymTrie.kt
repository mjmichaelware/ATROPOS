/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

/**
 * ATOM-TAX-013: Deduplicated Synonym Trie & Variant Mapping Engine.
 *
 * Maps standard and alternative command variants to canonical ATROPOS command namespaces.
 */
class CommandSynonymTrie {

    private val root = SynonymNode("")

    data class SynonymNode(
        val token: String,
        var targetCommand: String? = null,
        val children: MutableMap<String, SynonymNode> = mutableMapOf()
    )

    data class Match(
        val synonym: String,
        val targetCommand: String
    )

    init {
        registerAllSynonyms()
    }

    fun register(variant: String, canonicalCommand: String) {
        val cleanVariant = variant.trim().lowercase().removePrefix("/")
        val tokens = cleanVariant.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return

        var current = root
        for (token in tokens) {
            current = current.children.getOrPut(token) { SynonymNode(token) }
        }
        current.targetCommand = canonicalCommand
    }

    fun resolve(input: String): String? {
        val clean = input.trim().lowercase().removePrefix("/")
        val tokens = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        var current = root
        for (token in tokens) {
            current = current.children[token] ?: return null
        }
        return current.targetCommand
    }

    fun search(query: String): List<Match> {
        val needle = query.trim().lowercase().removePrefix("/")
        val matches = mutableListOf<Match>()
        fun dfs(node: SynonymNode, path: String) {
            node.targetCommand?.let { target ->
                if (needle.isEmpty() || path.contains(needle) || target.contains(needle)) {
                    matches.add(Match(path, target))
                }
            }
            node.children.forEach { (tok, child) ->
                val nextPath = if (path.isEmpty()) tok else "$path $tok"
                dfs(child, nextPath)
            }
        }
        dfs(root, "")
        return matches.sortedBy { it.synonym }
    }

    private fun registerAllSynonyms() {
        // status maps usage cost tokens context info stats telemetry
        listOf("usage", "cost", "tokens", "context", "info", "stats", "telemetry").forEach {
            register(it, "/status")
        }

        // diff maps changes patch uncommitted diff-view
        listOf("changes", "patch", "uncommitted", "diff-view").forEach {
            register(it, "/diff")
        }

        // interrupt maps pause freeze halt stop suspend
        listOf("pause", "freeze", "halt", "stop", "suspend").forEach {
            register(it, "/interrupt")
        }

        // resume maps continue unpause thaw
        listOf("continue", "unpause", "thaw").forEach {
            register(it, "/resume")
        }

        // storage maps gc compact clear purge prune reclaim
        listOf("gc", "compact", "clear", "purge", "prune", "reclaim").forEach {
            register(it, "/storage")
        }

        // memory maps context-store recall learned codebase-query
        listOf("context-store", "recall", "learned", "codebase-query").forEach {
            register(it, "/memory")
        }

        // history maps timeline trajectory log session-list
        listOf("timeline", "trajectory", "log", "session-list", "session_list").forEach {
            register(it, "/history")
        }

        // doctor maps checkup probe health diagnostics
        listOf("checkup", "probe", "health", "diagnostics").forEach {
            register(it, "/doctor")
        }

        // verify maps test lint validate check test-matrix
        listOf("test", "lint", "validate", "check", "test-matrix").forEach {
            register(it, "/verify")
        }

        // verbosity maps debug trace-level log-level
        listOf("debug", "trace-level", "log-level", "--verbose").forEach {
            register(it, "/system verbosity")
        }

        // theme maps color-scheme palette colors
        listOf("color-scheme", "palette", "colors").forEach {
            register(it, "/theme")
        }

        // ask-isolated maps btw side-query parallel-ask
        listOf("btw", "side-query", "parallel-ask").forEach {
            register(it, "/ps")
        }

        // compact maps squash condense summarize
        listOf("squash", "condense", "summarize", "auto-compact").forEach {
            register(it, "/compact")
        }

        // batch maps parallel fan-out multi-lane
        listOf("parallel", "fan-out", "multi-lane", "--workers").forEach {
            register(it, "/batch")
        }

        // fork maps branch split duplicate
        listOf("branch", "split", "duplicate").forEach {
            register(it, "/session fork")
        }

        // rollback maps rewind undo revert restore
        listOf("rewind", "undo", "revert", "restore").forEach {
            register(it, "/repo rollback")
        }

        // pr-fix maps autofix-pr review-pr pr-lint
        listOf("autofix-pr", "review-pr", "pr-lint", "actions.open_pr").forEach {
            register(it, "/github pr-fix")
        }

        // architect maps dual-model plan-execute ultraplan
        listOf("dual-model", "plan-execute", "ultraplan").forEach {
            register(it, "/architect")
        }

        // teleport maps tp web-lift mirror-open
        listOf("tp", "web-lift", "mirror-open", "web").forEach {
            register(it, "/session teleport")
        }

        // simplify maps clean refactor de-duplicate
        listOf("clean", "refactor", "de-duplicate").forEach {
            register(it, "/simplify")
        }

        // grep maps search find-text rg code-search
        listOf("search", "find-text", "rg", "code-search").forEach {
            register(it, "/system grep")
        }

        // alias maps bind-cmd shortcut-set
        listOf("bind-cmd", "shortcut-set", "macro").forEach {
            register(it, "/system alias")
        }

        // config maps settings preferences set get configure
        listOf("settings", "preferences", "set", "get", "configure").forEach {
            register(it, "/config")
        }

        // /session exit
        listOf("quit", "bye", "disconnect").forEach {
            register(it, "/session exit")
        }
    }
}
