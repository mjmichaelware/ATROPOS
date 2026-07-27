/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry
import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

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

    /**
     * Command palette in the pinned reference's dialog shape: a titled panel on
     * a raised surface, rows padded two columns in, the selected row carrying
     * the accent selection fill.
     *
     * The reference titles this dialog "Commands" and renders it as a
     * DialogSelect over a panel background rather than as a bare inline list.
     */
    fun render(query: CommandPaletteQuery?, width: Int, maximumRows: Int): List<String> {
        if (query == null || maximumRows <= 0) return emptyList()

        // One row is spent on the title, matching the reference's dialog header.
        val rowBudget = (maximumRows - 1).coerceAtLeast(1)
        val matches = CommandRegistry.slashMatches(query.text).take(rowBudget)
        if (matches.isEmpty()) return emptyList()

        val selected = query.selectedIndex.coerceIn(0, matches.lastIndex)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)

        return buildList {
            add(
                TerminalText.padEnd(
                    theme.paint(Role.BRAND, pad + "Commands") +
                        theme.subdued("  ${matches.size}"),
                    width
                )
            )
            matches.forEachIndexed { index, item ->
                add(renderItem(item, width, index == selected))
            }
        }
    }

    private fun renderItem(
        item: CommandEntry,
        width: Int,
        selected: Boolean
    ): String {
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val available = (
            width - TerminalText.cellWidth(pad) -
                TerminalText.cellWidth(item.command) - 3
            ).coerceAtLeast(0)
        val description = TerminalText.ellipsize(item.description, available)

        return if (selected) {
            // Reference fills the whole selected row with the accent.
            theme.paint(
                Role.ACCENT_SELECTION,
                TerminalText.padEnd(
                    pad + item.command +
                        if (description.isEmpty()) "" else "  $description",
                    width
                )
            )
        } else {
            TerminalText.padEnd(
                pad + theme.strong(item.command) +
                    if (description.isEmpty()) "" else theme.subdued("  $description"),
                width
            )
        }
    }
}
