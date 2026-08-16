/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandPaletteLevel
import atropos.cli.input.CommandRegistry
import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

data class CommandPaletteQuery(
    val text: String,
    val selectedIndex: Int = 0,
    val level: CommandPaletteLevel = CommandPaletteLevel.COMMANDS,
    val selectedGroup: String? = null,
    val selectedCommand: String? = null
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

        // One row is spent on the title. Rows are windowed around the selection,
        // so ArrowDown can traverse the complete result set instead of only the
        // first four rows visible in a short terminal.
        val rowBudget = (maximumRows - 1).coerceAtLeast(1)
        val matches = when (query.level) {
            CommandPaletteLevel.GROUPS -> emptyList()
            CommandPaletteLevel.COMMANDS -> query.selectedGroup
                ?.let { group -> CommandRegistry.helpSections().firstOrNull { it.category == group }?.entries.orEmpty() }
                ?: CommandRegistry.search(query.text)
            CommandPaletteLevel.DETAIL -> query.selectedCommand
                ?.let { command -> CommandRegistry.entries.firstOrNull { it.command == command } }
                ?.let(::listOf)
                .orEmpty()
        }
        val groups = if (query.level == CommandPaletteLevel.GROUPS) CommandRegistry.helpSections() else emptyList()
        if (query.level != CommandPaletteLevel.GROUPS && matches.isEmpty()) return emptyList()
        if (query.level == CommandPaletteLevel.GROUPS && groups.isEmpty()) return emptyList()

        val selected = query.selectedIndex.coerceIn(
            0,
            ((if (query.level == CommandPaletteLevel.GROUPS) groups.size else matches.size) - 1).coerceAtLeast(0)
        )
        val windowBudget = if (query.level == CommandPaletteLevel.GROUPS) (rowBudget - 1).coerceAtLeast(1) else rowBudget
        val start = windowStart(selected, if (query.level == CommandPaletteLevel.GROUPS) groups.size else matches.size, windowBudget)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)

        return buildList {
            add(
                TerminalText.padEnd(
                    theme.paint(Role.BRAND, pad + "Commands") +
                        theme.subdued("  ${if (query.level == CommandPaletteLevel.GROUPS) "groups" else matches.size}"),
                    width
                )
            )
            when (query.level) {
                CommandPaletteLevel.GROUPS -> {
                    add(TerminalText.padEnd(pad + theme.subdued("Pinned: ") + CommandRegistry.quickAccessCommands().joinToString(" · "), width))
                    groups.drop(start).take(windowBudget).forEachIndexed { offset, group ->
                        add(renderGroup(group.category, group.entries.size, width, start + offset == selected))
                    }
                }
                CommandPaletteLevel.COMMANDS -> matches.drop(start).take(rowBudget).forEachIndexed { offset, item ->
                    add(renderItem(item, width, start + offset == selected))
                }
                CommandPaletteLevel.DETAIL -> matches.forEach { addAll(renderDetail(it, width)) }
            }
        }
    }

    private fun windowStart(selected: Int, size: Int, budget: Int): Int {
        if (size <= budget) return 0
        return (selected - budget / 2).coerceIn(0, size - budget)
    }

    private fun renderGroup(category: String, count: Int, width: Int, selected: Boolean): String {
        val text = "${category.padEnd(14)} $count commands"
        return if (selected) theme.paint(
            Role.ACCENT_SELECTION,
            TerminalText.padEnd(
                TerminalText.ellipsize("  ${theme.focus(text)}", width.coerceAtLeast(1)),
                width.coerceAtLeast(1)
            )
        )
        else TerminalText.padEnd("  " + theme.strong(text), width)
    }

    private fun renderDetail(item: CommandEntry, width: Int): List<String> = buildList {
        add(theme.paint(Role.BRAND, "  ${item.command}"))
        addAll(AnsiLineWrapper.wrap(item.description, width.coerceAtLeast(1)).map { "  $it" })
        add("  risk: ${item.risk.label}")
        if (item.aliases.isNotEmpty()) add("  aliases: ${item.aliases.joinToString(" · ")}")
        if (item.related.isNotEmpty()) add("  related: ${item.related.joinToString(" · ")}")
        item.example?.let { add("  example: $it") }
        item.nlHint?.let { add("  NL hint: $it") }
    }

    private fun renderItem(
        item: CommandEntry,
        width: Int,
        selected: Boolean
    ): String {
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val safeWidth = width.coerceAtLeast(1)
        val available = (
            safeWidth - TerminalText.cellWidth(pad) -
                TerminalText.cellWidth(item.command) - 3
            ).coerceAtLeast(0)
        val description = TerminalText.ellipsize(item.description, available)

        return if (selected) {
            // Reference fills the whole selected row with the accent.
            theme.paint(
                Role.ACCENT_SELECTION,
                TerminalText.padEnd(
                    pad + item.command +
                        if (description.isEmpty()) "" else "  $description  [${item.risk.label}]",
                    safeWidth
                )
            )
        } else {
            TerminalText.padEnd(
                pad + theme.strong(item.command) +
                    if (description.isEmpty()) "" else theme.subdued("  $description  [${item.risk.label}]"),
                safeWidth
            )
        }
    }
}
