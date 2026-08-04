/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

data class CommandEntry(
    val command: String,
    val description: String,
    val category: String = "General",
    val aliases: List<String> = emptyList(),
    val risk: CommandRisk = CommandRiskCatalog.forCommand(command),
    val keywords: List<String> = emptyList(),
    val related: List<String> = emptyList(),
    val example: String? = null,
    val nlHint: String? = null
)

data class CommandGroup(
    val category: String,
    val entries: List<CommandEntry>
)

enum class CommandPaletteLevel { GROUPS, COMMANDS, DETAIL }

data class CommandPaletteSelection(
    val level: CommandPaletteLevel = CommandPaletteLevel.GROUPS,
    val group: String? = null,
    val command: String? = null,
    val index: Int = 0
)

sealed class CommandPaletteAction {
    data object Stay : CommandPaletteAction()
    data class Execute(val command: String) : CommandPaletteAction()
}

/** Pure keyboard navigation for the grouped palette. It never executes a command. */
class CommandPaletteNavigator(
    initial: CommandPaletteSelection = CommandPaletteSelection()
) {
    var selection: CommandPaletteSelection = initial
        private set

    fun move(delta: Int): CommandPaletteSelection {
        val values = currentValues()
        val next = if (values.isEmpty()) 0 else {
            (selection.index + delta).coerceIn(0, values.lastIndex)
        }
        selection = selection.copy(
            index = next,
            group = if (selection.level == CommandPaletteLevel.GROUPS) values.getOrNull(next) else selection.group,
            command = if (selection.level != CommandPaletteLevel.GROUPS) values.getOrNull(next) else selection.command
        )
        return selection
    }

    fun right(): CommandPaletteSelection {
        val values = currentValues()
        if (values.isEmpty()) return selection
        selection = when (selection.level) {
            CommandPaletteLevel.GROUPS -> selection.copy(
                level = CommandPaletteLevel.COMMANDS,
                group = values[selection.index.coerceIn(0, values.lastIndex)],
                index = 0,
                command = CommandRegistry.helpSections()
                    .firstOrNull { it.category == values[selection.index.coerceIn(0, values.lastIndex)] }
                    ?.entries?.firstOrNull()?.command
            )
            CommandPaletteLevel.COMMANDS -> selection.copy(
                level = CommandPaletteLevel.DETAIL,
                command = values[selection.index.coerceIn(0, values.lastIndex)]
            )
            CommandPaletteLevel.DETAIL -> selection
        }
        return selection
    }

    fun left(): CommandPaletteSelection {
        selection = when (selection.level) {
            CommandPaletteLevel.GROUPS -> selection
            CommandPaletteLevel.COMMANDS -> selection.copy(level = CommandPaletteLevel.GROUPS, group = null, command = null, index = 0)
            CommandPaletteLevel.DETAIL -> selection.copy(level = CommandPaletteLevel.COMMANDS, command = null)
        }
        return selection
    }

    fun enter(): CommandPaletteAction = when (selection.level) {
        CommandPaletteLevel.GROUPS -> CommandPaletteAction.Stay
        CommandPaletteLevel.COMMANDS, CommandPaletteLevel.DETAIL ->
            selection.command?.let(CommandPaletteAction::Execute) ?: CommandPaletteAction.Stay
    }

    private fun currentValues(): List<String> = when (selection.level) {
        CommandPaletteLevel.GROUPS -> CommandRegistry.helpSections().map { it.category }
        CommandPaletteLevel.COMMANDS -> CommandRegistry.helpSections()
            .firstOrNull { it.category == selection.group }
            ?.entries.orEmpty().map { it.command }
        CommandPaletteLevel.DETAIL -> listOfNotNull(selection.command)
    }
}

object CommandRegistry {
    private val catalog: List<CommandEntry> = CommandCatalog.catalog

    val entries: List<CommandEntry> = catalog
        .flatMap { it.expandedEntries() }
        .map { it.copy(category = normalizeCategory(it.category, it.command), keywords = keywordIndex(it)) }
        .distinctBy { it.command }

    private val canonicalEntries: List<CommandEntry> = catalog
        .map { it.copy(category = normalizeCategory(it.category, it.command), keywords = keywordIndex(it)) }

    val categories: List<String> = CATEGORY_ORDER
        .filter { category -> canonicalEntries.any { it.category == category } }

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
        canonicalEntries
            .groupBy { it.category }
            .toSortedMap(compareBy { CATEGORY_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE })
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
        return canonicalEntries
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
                        aliases = listOf(command) + aliases.filterNot { it == alias },
                        risk = risk,
                        keywords = keywords,
                        related = related,
                        example = example,
                        nlHint = nlHint
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
        val keywordMatches = keywords.any { it.contains(query, ignoreCase = true) }
        val keywordPrefixMatches = keywords.any { it.startsWith(query, ignoreCase = true) }
        val commandStarts = commandText.startsWith(query, ignoreCase = true)
        val commandContains = commandText.contains(query, ignoreCase = true)

        if (!commandStarts && !commandContains && !aliasTextMatches && !descriptionMatches && !keywordMatches) {
            return null
        }

        return when {
            commandText.equals(query, ignoreCase = true) -> 0
            aliasExactMatches -> 1
            commandStarts -> 2
            aliasPrefixMatches -> 3
            commandContains -> 4
            aliasTextMatches -> 5
            keywordPrefixMatches -> 6
            keywordMatches -> 7
            descriptionMatches -> 8
            else -> 9
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

    private fun normalizeCategory(category: String, command: String): String {
        val value = "$category $command".lowercase()
        return when {
            value.contains("model") || value.contains("provider") || value.contains("route") || value.contains("quota") -> "Models"
            value.contains("factory") || value.contains("artifact") || value.contains("build") || value.contains("test") -> "Build"
            value.contains("self-host") -> "Self-host"
            value.contains("agent") || value.contains("repair") -> "Agent"
            value.contains("dloi") || value.contains("ast") || value.contains("source") || value.contains("authority") -> "Authority"
            value.contains("verify") || value.contains("govern") || value.contains("audit") -> "Governance"
            value.contains("shell") || value.startsWith("shell ") || value.contains(" /cd") || value.contains(" /ls") -> "Shell"
            value.contains("key") || value.contains("paid") -> "Keys/Paid"
            value.contains("status") || value.contains("observe") || value.contains("debug") || value.contains("verbose") -> "Observe"
            value.contains("autonomous") || value.contains("daemon") -> "Autonomous"
            value.contains("tab") || value.contains("session") || value.contains("home") || value.contains("dashboard") -> "Session"
            else -> "Orient"
        }
    }

    private fun keywordIndex(entry: CommandEntry): List<String> =
        (entry.keywords + when {
            entry.command.startsWith("/factory") -> listOf("factory", "app", "application", "project", "scaffold")
            entry.command.startsWith("/providers") || entry.command.startsWith("/use") || entry.command == "/route" -> listOf("provider", "providers", "model", "route", "routing")
            entry.command.startsWith("/self-host") || entry.command.startsWith("/agent self-host") -> listOf("self-host", "self build", "phase 11", "inside out")
            entry.command.startsWith("/build") || entry.command.startsWith("/artifact") -> listOf("build", "compile", "artifact", "package")
            entry.command.startsWith("/status") || entry.command.startsWith("/verify") -> listOf("observe", "inspection", "evidence", "check")
            else -> emptyList()
        }).distinct()

    private val CATEGORY_ORDER = listOf(
        "Orient", "Models", "Build", "Agent", "Self-host", "Authority",
        "Governance", "Shell", "Keys/Paid", "Observe", "Autonomous", "Session"
    )
}
