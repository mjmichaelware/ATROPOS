/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry

/**
 * Help at three densities, all derived from the command registry.
 *
 * `SUP.UX.HELP-GENERATOR`: "Help is always complete and never drifts from
 * implementation; discovery cost for new commands is constant. Competitors
 * require manual help maintenance."
 *
 * Never drifts is a structural claim, and it holds only because nothing here
 * contains a command name. Every line is generated from
 * [CommandRegistry.entries], so a command that exists is documented and a
 * command that is documented exists — the registry's parity guard already
 * fails the build when routing and registration disagree.
 *
 * The three levels exist because the same operator needs different things at
 * different moments, and one density serves none of them:
 *
 * - [HelpLevel.SUMMARY] answers "what can I do here?" for someone who has just
 *   opened the CLI. It shows categories and the shortest command in each,
 *   because a first screen that scrolls has already failed.
 * - [HelpLevel.FULL] answers "what is the command for X?" — every command with
 *   its description, grouped.
 * - [HelpLevel.EXPERT] answers "what will this actually do to my machine?" and
 *   adds the policy class and the aliases. That is the level someone reads
 *   before letting an autonomous run touch a repository, so it says which
 *   commands are risky rather than making them look like the rest.
 */
class HelpGenerator(private val registry: HelpSource = RegistryHelpSource) {

    fun render(level: HelpLevel, filter: String = ""): List<String> {
        val query = filter.trim()
        val entries = if (query.isEmpty()) registry.entries() else registry.search(query)

        if (entries.isEmpty()) {
            return listOf(
                "No command matches '$query'.",
                "Try '/help' with no argument, or '/help ${query.take(3)}' for a broader match."
            )
        }

        // Every level carries the same header. Help has to be recognisable as
        // help on a terminal that has just scrolled a build log past it, and
        // the level belongs in the header rather than being something the
        // reader infers from how dense the output looks.
        return listOf("COMMANDS (${level.canonical})", "") + when (level) {
            HelpLevel.SUMMARY -> summary(entries)
            HelpLevel.FULL -> full(entries)
            HelpLevel.EXPERT -> expert(entries)
        }
    }

    /**
     * One line per category, naming the shortest commands in it.
     *
     * Shortest rather than first: within a family the shortest command is the
     * root (`/status` before `/status wide`), which is the one a newcomer
     * should try.
     */
    private fun summary(entries: List<CommandEntry>): List<String> =
        entries.groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> CommandRegistry.categories.indexOf(category).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .map { (category, commands) ->
                val shown = commands.sortedBy { it.command.length }.take(SUMMARY_PER_CATEGORY)
                "${category.padEnd(12)} ${shown.joinToString("  ") { it.command }}"
            } + listOf("", "'/help full' for every command, '/help expert' to see what each one can touch.")

    private fun full(entries: List<CommandEntry>): List<String> = buildList {
        entries.groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> CommandRegistry.categories.indexOf(category).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .forEach { (category, commands) ->
                add("[$category]")
                commands.sortedWith(compareBy({ it.command.length }, { it.command })).forEach { entry ->
                    add("  ${entry.command.padEnd(26)} ${entry.description}")
                }
                add("")
            }
    }.dropLastWhile { it.isBlank() }

    private fun expert(entries: List<CommandEntry>): List<String> = buildList {
        add("policy    command                    description")
        add("")
        entries.groupBy { it.category }
            .toList()
            .sortedBy { (category, _) -> CommandRegistry.categories.indexOf(category).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .forEach { (category, commands) ->
                add("[$category]")
                commands.sortedWith(compareBy({ it.command.length }, { it.command })).forEach { entry ->
                    add("  ${entry.risk.label.padEnd(9)} ${entry.command.padEnd(26)} ${entry.description}")
                    entry.example?.let { add("  ${" ".repeat(9)} e.g. $it") }
                    if (entry.aliases.isNotEmpty()) {
                        add("  ${" ".repeat(9)} also: ${entry.aliases.joinToString(", ")}")
                    }
                }
                add("")
            }
        add("'risky' commands change durable state or reach outside the process.")
    }.dropLastWhile { it.isBlank() }

    private companion object {
        /** A first screen that scrolls has already failed. */
        const val SUMMARY_PER_CATEGORY = 4
    }
}

enum class HelpLevel(val canonical: String) {
    SUMMARY("summary"),
    FULL("full"),
    EXPERT("expert");

    companion object {
        fun fromCanonical(term: String?): HelpLevel =
            entries.firstOrNull { it.canonical.equals(term?.trim(), ignoreCase = true) } ?: SUMMARY
    }
}

/**
 * Where the generator gets its commands.
 *
 * An interface so the generator can be tested against a known handful rather
 * than the whole live registry — a test asserting on every real command would
 * fail whenever anyone added one, which teaches people to stop reading it.
 */
interface HelpSource {
    fun entries(): List<CommandEntry>
    fun search(query: String): List<CommandEntry>
}

object RegistryHelpSource : HelpSource {
    override fun entries(): List<CommandEntry> = CommandRegistry.entries
    override fun search(query: String): List<CommandEntry> = CommandRegistry.search(query)
}
