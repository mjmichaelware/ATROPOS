/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandRegistry
import atropos.cli.ui.design.Role

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
        val category: String = "General",
        val aliases: List<String> = emptyList()
    )

    /**
     * Render a command palette showing available commands with help.
     */
    fun renderPalette(commands: List<Command>, width: Int, filterText: String = ""): List<String> {
        val safeWidth = width.coerceIn(1, 200)
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
        val safeWidth = width.coerceIn(1, 200)
        val output = mutableListOf<String>()

        output += theme.surface.sectionHeading(command.name, safeWidth, Role.BRAND)
        output += ""
        output += AnsiLineWrapper.wrap(command.description, safeWidth).map { theme.paint(Role.TEXT_PRIMARY, it) }
        output += ""

        if (command.shortcut != null) {
            output += theme.surface.row("Shortcut", theme.code(command.shortcut), safeWidth)
        }
        if (command.aliases.isNotEmpty()) {
            output += theme.surface.row(
                "Aliases",
                theme.code(command.aliases.joinToString(" · ")),
                safeWidth
            )
        }
        output += theme.surface.row("Category", theme.paint(Role.TEXT_SECONDARY, command.category), safeWidth)

        return output
    }

    /**
     * Render available slash commands (for "/" prefix).
     */
    fun renderSlashCommands(width: Int): List<String> {
        val safeWidth = width.coerceIn(1, 200)
        val slashCommands = buildList {
            add(Command("shell", "!cmd", "Execute a shell command in the project workspace", "Shell"))
            add(Command("pwd", "!/pwd", "Print working directory path", "Shell"))
            add(Command("cd", "!/cd <path>", "Change working directory", "Shell"))
            add(Command("ls", "!/ls", "List directory contents", "Shell"))
            add(Command("cat", "!/cat <path>", "Display file contents", "Shell"))
            add(Command("edit", "!/edit <path>", "Open file in editor", "Shell"))
            add(Command("search", "!/search <query>", "Search project files", "Search"))
            add(Command("grep", "!/grep <pattern>", "Search with pattern", "Search"))
            add(Command("clear", "!/clear", "Clear terminal screen", "System"))
            add(Command("theme", "!/theme", "Switch theme (dark/light/auto)", "System"))
            add(Command("settings", "!/settings", "Open settings", "System"))
            CommandRegistry.helpSections().forEach { group ->
                addAll(group.entries.map { entry ->
                    Command(
                        name = entry.command.removePrefix("/"),
                        shortcut = null,
                        description = entry.description,
                        category = group.category,
                        aliases = entry.aliases
                    )
                })
            }
        }

        val output = mutableListOf<String>()
        output += theme.surface.sectionHeading("Slash Commands", safeWidth, Role.BRAND)
        output += theme.metadata("Type / to search or use shortcut directly")
        output += ""

        for ((category, commands) in slashCommands.groupBy { it.category }.toSortedMap()) {
            output += theme.paint(Role.TEXT_SECONDARY, category)
            for (cmd in commands) {
                output += renderCommandRow(cmd, safeWidth)
            }
            output += ""
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
        val aliases = if (cmd.aliases.isEmpty()) {
            ""
        } else {
            "  " + theme.subdued("aliases ") +
                theme.code(cmd.aliases.joinToString(" · "))
        }
        val descWidth = (
            width -
                TerminalText.cellWidth(name) -
                TerminalText.cellWidth(shortcut) -
                TerminalText.cellWidth(aliases)
            ).coerceAtLeast(20)
        val desc = TerminalText.ellipsize(cmd.description, descWidth)
        return TerminalText.ellipsize(name + shortcut + theme.paint(Role.TEXT_PRIMARY, desc) + aliases, width)
    }
}
