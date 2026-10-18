/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.taxonomy.CommandFrequencyRankingStore
import atropos.cli.ui.design.Role

/**
 * ATOM-UI-017: Most-Used Header Renderer.
 *
 * Renders the persistent usage-frequency ranked header for command palettes
 * and modal sheets: top 3-5 commands under "MOST USED" followed by the
 * alphabetical remainder under "ALL COMMANDS".
 */
class MostUsedHeaderRenderer(
    private val theme: TerminalTheme,
    private val rankingStore: CommandFrequencyRankingStore = CommandFrequencyRankingStore()
) {
    data class RankedEntry(
        val command: String,
        val description: String,
        val frequency: Int,
        val isMostUsed: Boolean
    )

    fun partitionCommands(
        entries: List<Pair<String, String>>,
        limit: Int = 5
    ): Pair<List<RankedEntry>, List<RankedEntry>> {
        val allCommands = entries.map { it.first }
        val topCommands = rankingStore.topMostUsed(limit)
        val remainderCommands = rankingStore.alphabeticalRemainder(allCommands, limit)

        val entryMap = entries.toMap()

        val mostUsed = topCommands.mapNotNull { cmd ->
            val desc = entryMap[cmd] ?: return@mapNotNull null
            RankedEntry(
                command = cmd,
                description = desc,
                frequency = rankingStore.getCount(cmd),
                isMostUsed = true
            )
        }

        val allRemaining = remainderCommands.mapNotNull { cmd ->
            val desc = entryMap[cmd] ?: return@mapNotNull null
            RankedEntry(
                command = cmd,
                description = desc,
                frequency = rankingStore.getCount(cmd),
                isMostUsed = false
            )
        }

        return Pair(mostUsed, allRemaining)
    }

    fun render(
        entries: List<Pair<String, String>>,
        selectedIndex: Int,
        width: Int,
        limit: Int = 5
    ): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val lines = mutableListOf<String>()

        val (mostUsed, remainder) = partitionCommands(entries, limit)
        var currentIndex = 0

        if (mostUsed.isNotEmpty()) {
            lines.add(sectionHeader("MOST USED", safeWidth))
            for (entry in mostUsed) {
                lines.add(renderRow(entry, currentIndex == selectedIndex, safeWidth))
                currentIndex++
            }
        }

        lines.add(sectionHeader("ALL COMMANDS", safeWidth))
        for (entry in remainder) {
            lines.add(renderRow(entry, currentIndex == selectedIndex, safeWidth))
            currentIndex++
        }

        return lines
    }

    private fun sectionHeader(title: String, width: Int): String {
        val prefix = "─── $title "
        val dashCount = (width - prefix.length - 2).coerceAtLeast(2)
        val dashes = "─".repeat(dashCount)
        return theme.paint(Role.TEXT_MUTED, "  $prefix$dashes")
    }

    private fun renderRow(entry: RankedEntry, isSelected: Boolean, width: Int): String {
        val cursor = if (isSelected) "❯ " else "  "
        val cmdStyled = if (isSelected) {
            theme.paint(Role.ACCENT_FOCUS, entry.command.padEnd(20))
        } else {
            theme.paint(Role.TEXT_PRIMARY, entry.command.padEnd(20))
        }

        val descStyled = if (isSelected) {
            theme.paint(Role.TEXT_PRIMARY, entry.description)
        } else {
            theme.subdued(entry.description)
        }

        val badge = if (entry.isMostUsed && entry.frequency > 0) {
            " " + theme.paint(Role.TEXT_MUTED, "(${entry.frequency})")
        } else {
            ""
        }

        val raw = "$cursor$cmdStyled $descStyled$badge"
        return TerminalText.ellipsize(raw, width)
    }
}
