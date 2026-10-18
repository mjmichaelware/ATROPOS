/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.taxonomy.HierarchicalCommandTaxonomy
import atropos.cli.ui.design.Role

/**
 * ATOM-UI-016: 3-Column Progressive Disclosure Modal (? and /?).
 *
 * Implements standardized beautiful box framing for interactive cheatsheets:
 * Column 1: Shortcuts (canonical 34 mobile & desktop bindings).
 * Column 2: Commands (alphabetical trie with progressive drill-down descriptions).
 * Column 3: General Information (project, conversation hash, version, branch, provider).
 */
class QuestionMarkModalRenderer(
    private val theme: TerminalTheme
) {
    enum class ActiveView(val title: String) {
        SHORTCUTS("Shortcuts"),
        COMMANDS("Commands"),
        GENERAL_INFO("General Info")
    }

    private val taxonomy = HierarchicalCommandTaxonomy()
    private val mostUsedHeaderRenderer = MostUsedHeaderRenderer(theme)

    fun render(
        activeView: ActiveView,
        selectedIndex: Int,
        width: Int,
        project: String = "ATROPOS",
        conversationHash: String = "0x8f2a...c319",
        version: String = "10.0.1",
        branch: String = "main",
        provider: String = "gemini-2.5-pro",
        isLeafExpanded: Boolean = false
    ): List<String> {
        val safeWidth = width.coerceAtLeast(60)
        val innerWidth = safeWidth - 4
        val lines = mutableListOf<String>()

        val title = " ATROPOS CLI — Progressive Disclosure Modal (?) "
        lines.add(renderBoxHeader(title, safeWidth))

        val navKey = "Keyboard: ↑/↓ Navigate  ←/→ Switch View/Expand  enter Select  esc Close"
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, navKey.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        // Column tab bar
        val tabs = ActiveView.values().joinToString("  ") { view ->
            if (view == activeView) {
                theme.paint(Role.ACCENT_FOCUS, "[ ${view.title} ]")
            } else {
                theme.subdued("  ${view.title}  ")
            }
        }
        lines.add("│ " + TerminalText.ellipsize(tabs, innerWidth).padEnd(innerWidth) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        when (activeView) {
            ActiveView.SHORTCUTS -> renderShortcuts(lines, selectedIndex, innerWidth)
            ActiveView.COMMANDS -> renderCommands(lines, selectedIndex, innerWidth, isLeafExpanded)
            ActiveView.GENERAL_INFO -> renderGeneralInfo(lines, innerWidth, project, conversationHash, version, branch, provider)
        }

        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    fun renderCategoryDefinitions(categoryQuery: String = "", selectedIndex: Int = 0, width: Int = 80): List<String> {
        val safeWidth = width.coerceAtLeast(50)
        val innerWidth = safeWidth - 4
        val lines = mutableListOf<String>()

        val title = " ATROPOS — Category Definition Mode (/?) "
        lines.add(renderBoxHeader(title, safeWidth))

        val nav = "Keyboard: ↑/↓ Navigate  enter Step into  esc Return"
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, nav.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        val nodes = taxonomy.stepInto(listOf(categoryQuery).filter { it.isNotBlank() })
        if (nodes.isEmpty() || categoryQuery.isBlank()) {
            val rootEntries = taxonomy.root.entries.sortedBy { it.key }
            rootEntries.forEachIndexed { index, (name, node) ->
                val isSelected = index == selectedIndex
                val cursor = if (isSelected) "❯ " else "  "
                val catStyled = if (isSelected) theme.paint(Role.ACCENT_FOCUS, name.uppercase().padEnd(16)) else theme.paint(Role.TEXT_PRIMARY, name.uppercase().padEnd(16))
                val actionsCount = "[${node.children.size} actions]"
                val desc = "${node.description} $actionsCount"
                val descStyled = if (isSelected) theme.strong(desc) else theme.subdued(desc)
                val row = "$cursor$catStyled $descStyled"
                lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
            }
        } else {
            nodes.forEachIndexed { index, node ->
                val isSelected = index == selectedIndex
                val cursor = if (isSelected) "❯ " else "  "
                val tokStyled = if (isSelected) theme.paint(Role.ACCENT_FOCUS, node.token.padEnd(16)) else theme.paint(Role.TEXT_PRIMARY, node.token.padEnd(16))
                val desc = node.leafResolution ?: node.description
                val descStyled = if (isSelected) theme.strong(desc) else theme.subdued(desc)
                val row = "$cursor$tokStyled $descStyled"
                lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
            }
        }

        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    private fun renderShortcuts(lines: MutableList<String>, selectedIndex: Int, innerWidth: Int) {
        val shortcuts = listOf(
            "alt+m" to "Send with opposite Queued Messages setting",
            "alt+v, ctrl+v" to "Paste image/video/text from clipboard",
            "ctrl+_, ctrl+shift+-" to "Undo last prompt buffer modification",
            "ctrl+c, esc" to "Dismiss modal / cancel active pipeline",
            "ctrl+d" to "Exit CLI cleanly",
            "ctrl+end" to "Jump transcript viewport to bottom",
            "ctrl+g" to "Open active prompt buffer in external \$EDITOR",
            "ctrl+home" to "Jump transcript viewport to top",
            "ctrl+k" to "Fast-path approve active proposal or gate",
            "ctrl+l" to "Clear CLI screen buffer",
            "ctrl+o" to "Toggle trajectory and thinking view",
            "ctrl+r" to "Open interactive artifact review",
            "ctrl+shift+z" to "Redo undone prompt buffer modification",
            "ctrl+y" to "Yank text from kill ring into buffer",
            "ctrl+z" to "Suspend CLI process to background",
            "down" to "Move down in active lists",
            "enter" to "Send message or confirm selection",
            "f2" to "Rename active tab or workspace",
            "f4" to "Delete active item",
            "f5 / #F12" to "Start voice dictation input",
            "left" to "Move left / Previous tab",
            "pgdown" to "Page down transcript",
            "pgup" to "Page up transcript",
            "right" to "Move right / Next tab / Expand doc",
            "shift+down" to "Scroll transcript down half page",
            "shift+tab" to "Cycle mode (ASK, CODE, ARCHITECT)",
            "shift+up" to "Scroll transcript up half page",
            "shift/alt+click" to "Select and copy terminal text",
            "tab" to "Cycle focus or complete selection",
            "up" to "Move up in active lists"
        )

        shortcuts.take(16).forEachIndexed { index, (keys, action) ->
            val prefix = if (index == selectedIndex) "❯ " else "  "
            val keyStyled = theme.paint(Role.ACCENT_FOCUS, keys.padEnd(24))
            val actStyled = if (index == selectedIndex) theme.paint(Role.TEXT_PRIMARY, action) else theme.subdued(action)
            val row = "$prefix$keyStyled $actStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }
    }

    private fun renderCommands(lines: MutableList<String>, selectedIndex: Int, innerWidth: Int, isLeafExpanded: Boolean = false) {
        val commands = listOf(
            "/agent" to "Autonomous agent lifecycle and parallel dispatch",
            "/repo" to "CAS-backed repository inspection and atomic mutation",
            "/memory" to "Lakehouse episodic memory and learned heuristic recall",
            "/governance" to "Cryptographic policy gates, audit, and custodian",
            "/toolchain" to "Cross-platform compiler doctor, testing, and MCP",
            "/session" to "Context compact, transcript export, and web teleport",
            "/system" to "Theme selection, verbosity levels, aliases, and config",
            "/verify" to "Deterministic verification gate and test suite runner",
            "/help" to "Hierarchical command taxonomy and interactive guides",
            "/plan" to "Read-only plan mode without filesystem mutations",
            "/architect" to "2-model planner gate: plan vs verify",
            "/goal" to "Set long-horizon target and autonomous goal bounds",
            "/compact" to "LLM context reduction and context window pruning",
            "/batch" to "Schedule parallel wave lanes with disjoint locks",
            "/simplify" to "Refactor AST for complexity reduction",
            "/map" to "Semantic repository map across AST symbol lattices",
            "/lint" to "Execute syntax and AST linters emitting diagnostic vectors"
        )

        val rendered = mostUsedHeaderRenderer.render(commands, selectedIndex, innerWidth)
        rendered.forEach { rLine ->
            lines.add("│ " + TerminalText.ellipsize(rLine, innerWidth).padEnd(innerWidth) + " │")
        }

        if (isLeafExpanded && selectedIndex in commands.indices) {
            val selectedCmd = commands[selectedIndex].first
            val leaf = atropos.cli.help.CommandLeafRegistry.getOrDefault(selectedCmd, commands[selectedIndex].second)
            val leafLines = atropos.cli.help.CommandLeafRegistry.render(leaf, isExpanded = true, theme = theme, width = innerWidth)
            leafLines.forEach { lLine ->
                lines.add("│ " + TerminalText.ellipsize(lLine, innerWidth).padEnd(innerWidth) + " │")
            }
        }
    }

    private fun renderGeneralInfo(
        lines: MutableList<String>,
        innerWidth: Int,
        project: String,
        conversationHash: String,
        version: String,
        branch: String,
        provider: String
    ) {
        val infoItems = listOf(
            "Project" to project,
            "Branch" to branch,
            "Engine Version" to "v$version (DHE_ADAM)",
            "Active Provider" to provider,
            "Conversation Hash" to conversationHash,
            "Hardware Arch" to System.getProperty("os.arch"),
            "Operating System" to System.getProperty("os.name"),
            "Mediation Invariant" to "Cap(P_theta) ∩ Cap(A_write) = ∅ (ENFORCED)"
        )

        infoItems.forEach { (label, value) ->
            val labelStyled = theme.paint(Role.TEXT_MUTED, label.padEnd(24))
            val valueStyled = theme.paint(Role.TEXT_PRIMARY, value)
            val row = "$labelStyled : $valueStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }
    }

    private fun renderBoxHeader(title: String, width: Int): String {
        val cleanTitle = " $title "
        val rem = (width - cleanTitle.length - 2).coerceAtLeast(2)
        val left = rem / 2
        val right = rem - left
        return "╭" + "─".repeat(left) + cleanTitle + "─".repeat(right) + "╮"
    }
}
