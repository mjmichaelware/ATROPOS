/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

/**
 * ATOM-TAX-013: Deduplicated Synonym Trie & Variant Mapping Engine.
 *
 * Maps competitive command variants across Claude Code, Aider, Goose, SWE-Agent,
 * and Cursor/Antigravity to canonical ATROPOS command namespaces.
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
        // /session status
        listOf("usage", "cost", "tokens", "context", "info", "stats", "telemetry").forEach {
            register(it, "/session status")
        }

        // /repo diff
        listOf("changes", "patch", "uncommitted", "diff-view").forEach {
            register(it, "/repo diff")
        }

        // /agent pause
        listOf("pause", "interrupt", "freeze", "halt", "stop", "suspend").forEach {
            register(it, "/agent pause")
        }

        // /agent resume
        listOf("continue", "unpause", "thaw", "session_resume").forEach {
            register(it, "/agent resume")
        }

        // /system storage
        listOf("gc", "compact", "clear", "purge", "prune", "reclaim", "local-models_delete").forEach {
            register(it, "/system storage")
        }

        // /memory search
        listOf("context-store", "recall", "learned", "codebase-query", "@codebase", "@docs").forEach {
            register(it, "/memory search")
        }

        // /session history
        listOf("timeline", "trajectory", "log", "session-list", "session_list").forEach {
            register(it, "/session history")
        }

        // /toolchain doctor
        listOf("checkup", "probe", "health", "diagnostics").forEach {
            register(it, "/toolchain doctor")
        }

        // /verify run
        listOf("test", "lint", "validate", "check", "test-matrix").forEach {
            register(it, "/verify run")
        }

        // /system verbosity
        listOf("debug", "trace-level", "log-level", "--verbose").forEach {
            register(it, "/system verbosity")
        }

        // /system theme
        listOf("color-scheme", "palette", "colors").forEach {
            register(it, "/system theme")
        }

        // /agent ask-isolated
        listOf("btw", "side-query", "parallel-ask").forEach {
            register(it, "/agent ask-isolated")
        }

        // /session compact
        listOf("squash", "condense", "summarize", "auto-compact").forEach {
            register(it, "/session compact")
        }

        // /agent batch
        listOf("parallel", "fan-out", "multi-lane", "--workers").forEach {
            register(it, "/agent batch")
        }

        // /session fork
        listOf("branch", "split", "duplicate").forEach {
            register(it, "/session fork")
        }

        // /repo rollback
        listOf("rewind", "undo", "revert", "restore").forEach {
            register(it, "/repo rollback")
        }

        // /github pr-fix
        listOf("autofix-pr", "review-pr", "pr-lint", "actions.open_pr").forEach {
            register(it, "/github pr-fix")
        }

        // /agent architect
        listOf("dual-model", "plan-execute", "ultraplan", "plan").forEach {
            register(it, "/agent architect")
        }

        // /session teleport
        listOf("tp", "web-lift", "mirror-open", "web").forEach {
            register(it, "/session teleport")
        }

        // /agent simplify
        listOf("clean", "refactor", "de-duplicate").forEach {
            register(it, "/agent simplify")
        }

        // /system grep
        listOf("search", "find-text", "rg", "code-search").forEach {
            register(it, "/system grep")
        }

        // /system alias
        listOf("bind-cmd", "shortcut-set", "macro").forEach {
            register(it, "/system alias")
        }

        // /system config
        listOf("settings", "preferences", "set", "get", "configure").forEach {
            register(it, "/system config")
        }

        // /session exit
        listOf("quit", "bye", "disconnect").forEach {
            register(it, "/session exit")
        }
    }
}
