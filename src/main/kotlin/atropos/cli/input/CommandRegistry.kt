/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

data class CommandEntry(
    val command: String,
    val description: String,
    val category: String = "General",
    val aliases: List<String> = emptyList(),
    val risk: CommandRisk = CommandRiskCatalog.forCommand(command)
)

data class CommandGroup(
    val category: String,
    val entries: List<CommandEntry>
)

object CommandRegistry {
    private val catalog: List<CommandEntry> = CommandCatalog.catalog

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
