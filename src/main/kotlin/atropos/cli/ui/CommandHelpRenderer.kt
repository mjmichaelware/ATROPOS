/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandEntry
import atropos.bridge.menu.HelpRegistry

/**
 * Builds the lines of the command help block.
 *
 * Pulled out of [AnsiTerminalEngine] because it is pure text composition: given
 * a query and a width it returns lines, touching no terminal, no canvas, and no
 * frame scheduler. That is what makes it testable by calling it — the engine it
 * came from can only be exercised by driving a terminal.
 *
 * The engine keeps the decision of *where* the lines go (transcript buffer in
 * reactive mode, straight to stdout otherwise); this owns only what they say.
 *
 * ## Grouping is by leading verb
 *
 * Commands are grouped on their first word, so `/agent status` and `/agent run`
 * sit together under `agent`. A flat alphabetical list buries related commands
 * between unrelated ones, and the operator scanning for "what can I do with the
 * agent" has to read the whole list to find out.
 */
class CommandHelpRenderer(
    private val theme: TerminalTheme,
    private val railGlyph: () -> String = ::defaultRailGlyph
) {

    /**
     * @param query optional filter; a leading `/` is ignored so `/ag` and `ag` match alike.
     * @param width the viewport width lines are ellipsized to.
     */
    fun lines(query: String, width: Int): List<String> {
        val rail = theme.paint(atropos.cli.ui.design.Role.ACCENT_FOCUS, railGlyph())
        val safeWidth = width.coerceAtLeast(MINIMUM_WIDTH)
        val filter = query.trim().removePrefix("/")
        val entries = if (filter.isBlank()) HelpRegistry.commandEntries() else atropos.cli.input.CommandRegistry.search(filter)

        val lines = mutableListOf<String>()
        lines += rail + PAD + theme.brand("COMMANDS")

        if (filter.isNotBlank()) {
            lines += ellipsized(rail + PAD + filterSummary(filter, entries.size), safeWidth)
        }

        if (entries.isEmpty()) {
            lines += ellipsized(rail + PAD + emptySummary(filter), safeWidth)
        } else {
            lines += commandLines(entries, rail, safeWidth)
        }

        lines += ellipsized(rail + PAD + theme.subdued(FOOTER), safeWidth)
        return lines
    }

    private fun commandLines(entries: List<CommandEntry>, rail: String, width: Int): List<String> {
        // Aligned on the longest command, capped so one very long command cannot
        // push every description off the right edge of a narrow terminal.
        val labelWidth = entries.maxOfOrNull { it.command.length }?.coerceAtMost(MAXIMUM_LABEL_WIDTH)
            ?: DEFAULT_LABEL_WIDTH

        return group(entries).flatMap { (group, grouped) ->
            listOf(ellipsized(rail + PAD + theme.subdued("group ") + theme.code(group), width)) +
                grouped.map { entry ->
                    ellipsized(
                        rail + PAD +
                            theme.strong(TerminalText.padEnd(entry.command, labelWidth)) +
                            " " + theme.subdued(entry.description),
                        width
                    )
                }
        }
    }

    private fun filterSummary(filter: String, matchCount: Int): String =
        theme.subdued("filter: ") + theme.code(filter) +
            theme.subdued(" · $matchCount match${if (matchCount == 1) "" else "es"}")

    private fun emptySummary(filter: String): String =
        theme.subdued("no command matches") + if (filter.isBlank()) "" else " " + theme.code(filter)

    private fun group(entries: List<CommandEntry>): List<Pair<String, List<CommandEntry>>> =
        entries
            .groupBy { groupOf(it.command) }
            .toSortedMap()
            .map { (group, grouped) -> group to grouped.sortedBy(CommandEntry::command) }

    private fun groupOf(command: String): String =
        command.substringBefore(' ').trim().ifBlank { command }

    private fun ellipsized(line: String, width: Int): String = TerminalText.ellipsize(line, width)

    private companion object {
        const val PAD = "  "
        const val FOOTER = "? | /help | /usage | /self-host"
        const val MINIMUM_WIDTH = 40
        const val MAXIMUM_LABEL_WIDTH = 32
        const val DEFAULT_LABEL_WIDTH = 20

        /**
         * Terminals that cannot draw the box-drawing rail fall back to a pipe.
         * `ATROPOS_ASCII` is the existing opt-out; honouring it here keeps help
         * legible on the terminals that set it.
         */
        fun defaultRailGlyph(): String =
            if (System.getenv("ATROPOS_ASCII").isNullOrBlank()) "┃" else "|"
    }
}
