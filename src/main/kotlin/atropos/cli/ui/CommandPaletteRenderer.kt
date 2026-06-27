/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

data class CommandPaletteItem(
    val command: String,
    val description: String
)

class CommandPaletteRenderer(
    private val theme: TerminalTheme
) {
    private val commands = listOf(
        CommandPaletteItem("/help", "commands"),
        CommandPaletteItem("/status", "session status"),
        CommandPaletteItem("/status endpoints", "operation registry"),
        CommandPaletteItem("/providers", "provider inventory"),
        CommandPaletteItem("/providers descriptors", "provider contract grid"),
        CommandPaletteItem("/providers validate", "provider descriptor validation"),
        CommandPaletteItem("/route", "preview routing decision"),
        CommandPaletteItem("/factory run", "local app-factory queue"),
        CommandPaletteItem("/factory plan", "bounded app-factory plan"),
        CommandPaletteItem("/paid status", "paid lock state"),
        CommandPaletteItem("/assets svg", "create local svg asset"),
        CommandPaletteItem("/status paid", "paid emergency gate"),
        CommandPaletteItem("/status assets", "asset provider status"),
        CommandPaletteItem("/use auto", "restore automatic routing"),
        CommandPaletteItem("/verify narrow", "quick verification"),
        CommandPaletteItem("/verify wide", "wide verification"),
        CommandPaletteItem("/exit", "close session"),
        CommandPaletteItem("/quit", "close session")
    )

    fun render(query: String?, width: Int, maximumRows: Int): List<String> {
        if (query == null || maximumRows <= 0) return emptyList()
        val matches = commands.filter {
            it.command.startsWith(query) || it.command.contains(query)
        }.take(maximumRows)

        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, item ->
            val marker = if (index == 0) "› " else "  "
            val available = (width -
                TerminalText.cellWidth(marker) -
                TerminalText.cellWidth(item.command) - 3
            ).coerceAtLeast(0)
            val description = TerminalText.ellipsize(item.description, available)
            val plain = marker + item.command +
                if (description.isEmpty()) "" else " · $description"

            if (index == 0) {
                theme.selection(TerminalText.padEnd(plain, width))
            } else {
                theme.metadata(plain)
            }
        }
    }
}
