/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry

data class CommandPaletteQuery(
    val text: String,
    val selectedIndex: Int = 0
)

class CommandPaletteRenderer(
    private val theme: TerminalTheme
) {
    fun render(query: String?, width: Int, maximumRows: Int): List<String> {
        val paletteQuery = query?.let {
            CommandPaletteQuery(it, 0)
        }
        return render(paletteQuery, width, maximumRows)
    }

    fun render(query: CommandPaletteQuery?, width: Int, maximumRows: Int): List<String> {
        if (query == null || maximumRows <= 0) return emptyList()

        val matches = CommandRegistry
            .slashMatches(query.text)
            .take(maximumRows)

        if (matches.isEmpty()) return emptyList()

        val selected = query.selectedIndex.coerceIn(0, matches.lastIndex)

        return matches.mapIndexed { index, item ->
            renderItem(
                item = item,
                width = width,
                selected = index == selected
            )
        }
    }

    private fun renderItem(
        item: CommandEntry,
        width: Int,
        selected: Boolean
    ): String {
        val marker = if (selected) "> " else "  "
        val available = (
            width -
                TerminalText.cellWidth(marker) -
                TerminalText.cellWidth(item.command) -
                3
            ).coerceAtLeast(0)
        val description = TerminalText.ellipsize(item.description, available)
        val plain = marker + item.command +
            if (description.isEmpty()) "" else " · $description"

        return if (selected) {
            theme.selection(TerminalText.padEnd(plain, width))
        } else {
            theme.metadata(plain)
        }
    }
}
