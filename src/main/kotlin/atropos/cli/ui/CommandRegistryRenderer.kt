/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.cli.ui.design.Spacing

/**
 * Renders available commands and slash-command help.
 * Displays command palette for CLI with keyboard shortcuts and descriptions.
 */
class CommandRegistryRenderer(
    private val theme: TerminalTheme
) {
    data class Command(
        val name: String,
        val shortcut: String? = null,
        val description: String,
        val category: String = "General"
    )

    /**
     * Render a command palette showing available commands with help.
     */
    fun renderPalette(commands: List<Command>, width: Int, filterText: String = ""): List<String> {
        val safeWidth = width.coerceIn(40, 200)
        val output = mutableListOf<String>()

        output += theme.surface.sectionHeading("Commands", safeWidth, Role.BRAND)
        if (filterText.isNotBlank()) {
            output += theme.metadata("Filter: ") + theme.code(filterText)
        }
        output += ""

        // Group by category
        val byCategory = commands.groupBy { it.category }
        for ((category, cmds) in byCategory.toSortedMap()) {
            output += theme.paint(Role.TEXT_SECONDARY, category)
            for (cmd in cmds) {
                output += renderCommandRow(cmd, safeWidth)
            }
            output += ""
        }

        output += theme.metadata("Type a command name or press / for palette. Ctrl+C to exit.")
        return output
    }

    /**
     * Render detailed help for a single command.
     */
    fun renderHelp(command: Command, width: Int): List<String> {
        val safeWidth = width.coerceIn(40, 200)
        val output = mutableListOf<String>()

        output += theme.surface.sectionHeading(command.name, safeWidth, Role.BRAND)
        output += ""
        output += AnsiLineWrapper.wrap(command.description, safeWidth).map { theme.paint(Role.TEXT_PRIMARY, it) }
        output += ""

        if (command.shortcut != null) {
            output += theme.surface.row("Shortcut", theme.code(command.shortcut), safeWidth)
        }
        output += theme.surface.row("Category", theme.paint(Role.TEXT_SECONDARY, command.category), safeWidth)

        return output
    }

    /**
     * Render available slash commands (for "/" prefix).
     */
    fun renderSlashCommands(width: Int): List<String> {
        val safeWidth = width.coerceIn(40, 200)
        val slashCommands = listOf(
            Command("shell", "!cmd", "Execute a shell command in the project workspace", "Shell"),
            Command("pwd", "!/pwd", "Print working directory path", "Shell"),
            Command("cd", "!/cd <path>", "Change working directory", "Shell"),
            Command("ls", "!/ls", "List directory contents", "Shell"),
            Command("cat", "!/cat <path>", "Display file contents", "Shell"),
            Command("edit", "!/edit <path>", "Open file in editor", "Shell"),
            Command("search", "!/search <query>", "Search project files", "Search"),
            Command("grep", "!/grep <pattern>", "Search with pattern", "Search"),
            Command("help", "/?", "Show command help", "System"),
            Command("clear", "!/clear", "Clear terminal screen", "System"),
            Command("theme", "!/theme", "Switch theme (dark/light/auto)", "System"),
            Command("settings", "!/settings", "Open settings", "System")
        )

        val output = mutableListOf<String>()
        output += theme.surface.sectionHeading("Slash Commands", safeWidth, Role.BRAND)
        output += theme.metadata("Type / to search or use shortcut directly")
        output += ""

        for (cmd in slashCommands) {
            output += renderCommandRow(cmd, safeWidth)
        }

        return output
    }

    private fun renderCommandRow(cmd: Command, width: Int): String {
        val name = theme.code("/" + cmd.name.padEnd(12))
        val shortcut = if (cmd.shortcut != null) {
            "  " + theme.metadata(cmd.shortcut.padEnd(16))
        } else {
            "  " + " ".repeat(16)
        }
        val desc = TerminalText.ellipsize(cmd.description, (width - 36).coerceAtLeast(20))
        return TerminalText.ellipsize(name + shortcut + theme.paint(Role.TEXT_PRIMARY, desc), width)
    }
}
