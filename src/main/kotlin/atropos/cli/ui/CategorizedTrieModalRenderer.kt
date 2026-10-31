/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.help.CommandLeafRegistry
import atropos.cli.taxonomy.CommandFrequencyRankingStore
import atropos.cli.taxonomy.HierarchicalCommandTaxonomy
import atropos.cli.taxonomy.TaxonomyNode
import atropos.cli.ui.design.Role
import java.io.PrintStream

/**
 * ANSI Cursor Manipulation Utilities for Zero-Scrollback Floating Overlays.
 *
 * Uses ANSI save cursor (`\u001b[s`), relative cursor movement (`\u001b[<N>A`),
 * and line erase (`\u001b[2K`, `\u001b[1G`) to render interactive modal boxes
 * floating *above* the user's prompt input line without polluting the terminal history.
 */
object AnsiFloatingOverlay {
    const val SAVE_CURSOR = "\u001b[s"
    const val RESTORE_CURSOR = "\u001b[u"
    const val CLEAR_LINE = "\u001b[2K"
    const val COLUMN_1 = "\u001b[1G"

    fun cursorUp(n: Int): String = if (n > 0) "\u001b[${n}A" else ""
    fun cursorDown(n: Int): String = if (n > 0) "\u001b[${n}B" else ""

    /**
     * Builds an atomic ANSI escape batch that saves the cursor, moves UP by the
     * required line count, draws the box lines with line clearing, and restores
     * the cursor strictly to the input line.
     */
    fun composeFloatingBox(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val count = lines.size
        val sb = StringBuilder()
        sb.append(SAVE_CURSOR)
        sb.append(cursorUp(count))
        lines.forEachIndexed { index, line ->
            sb.append(COLUMN_1)
            sb.append(CLEAR_LINE)
            sb.append(line)
            if (index < count - 1) {
                sb.append(cursorDown(1))
            }
        }
        sb.append(RESTORE_CURSOR)
        return sb.toString()
    }

    /**
     * Builds an atomic ANSI escape batch that clears the previously rendered floating
     * box lines above the input line, leaving the scrollback 100% spotless.
     */
    fun composeEraseBox(lineCount: Int): String {
        if (lineCount <= 0) return ""
        val sb = StringBuilder()
        sb.append(SAVE_CURSOR)
        sb.append(cursorUp(lineCount))
        for (i in 0 until lineCount) {
            sb.append(COLUMN_1)
            sb.append(CLEAR_LINE)
            if (i < lineCount - 1) {
                sb.append(cursorDown(1))
            }
        }
        sb.append(RESTORE_CURSOR)
        return sb.toString()
    }
}

/**
 * ATOM-UI-019 / ATOM 6.2: Categorized Command Trie Floating Modal (/).
 *
 * Implements a terminal-native floating modal box rendered strictly ABOVE the current
 * CLI prompt line using ANSI cursor manipulation (\u001b[s, \u001b[<N>A, \u001b[u, \u001b[2K).
 *
 * Strictly aligned with Android APK CommandCategory:
 * - Tier 1 (CATEGORIES): 8 canonical Android categories (agent, providers, code, workspace, git, mcp, diagnostics, specgraph)
 * - Tier 2 (SUBCATEGORIES): Logical command groups within category
 * - Tier 3 (COMMANDS): Concrete executable command entries
 * - Tier 4 (LEAF_DOCS): Detailed progressive command card
 *
 * Zero Scrollback Pollution:
 * The modal never writes standard println lines to the scrollback. Upon closing or selection,
 * the floating box is wiped away completely with ANSI erase commands, restoring cursor locks.
 */
class CategorizedTrieModalRenderer(
    private val theme: TerminalTheme,
    val taxonomy: HierarchicalCommandTaxonomy = HierarchicalCommandTaxonomy(),
    private val rankingStore: CommandFrequencyRankingStore = CommandFrequencyRankingStore(),
    val overlayRenderer: TrieModalOverlayRenderer = TrieModalOverlayRenderer(theme, taxonomy)
) {
    enum class DrillLevel {
        CATEGORIES,
        SUBCATEGORIES,
        COMMANDS,
        LEAF_DOCS;

        companion object {
            val DOMAINS = CATEGORIES
            val SUBCATEGORIES_OR_ACTIONS = COMMANDS
        }
    }

    data class TrieNavState(
        val level: DrillLevel = DrillLevel.CATEGORIES,
        val selectedCategory: String = "agent",
        val selectedSubcategory: String? = null,
        val selectedCommand: String? = null,
        val selectedIndex: Int = 0
    ) {
        // Backwards compatibility aliases
        val selectedDomain: String get() = selectedCategory
        val selectedAction: String? get() = selectedCommand
    }

    /**
     * Renders the formatted box lines for the given state.
     */
    fun render(
        state: TrieNavState,
        width: Int,
        height: Int = 24
    ): List<String> {
        val safeWidth = width.coerceAtLeast(60)
        val lines = mutableListOf<String>()

        // 1. Box Top Border with Centered Title
        val titleText = when (state.level) {
            DrillLevel.CATEGORIES -> " ATROPOS CLI — Command Palette (/) "
            DrillLevel.SUBCATEGORIES -> " ATROPOS CLI — /${state.selectedCategory} Subcategories "
            DrillLevel.COMMANDS -> " ATROPOS CLI — /${state.selectedCategory} > ${state.selectedSubcategory ?: "Commands"} "
            DrillLevel.LEAF_DOCS -> " ATROPOS CLI — Command Card: ${state.selectedCommand ?: "/"} "
        }
        lines.add(renderBoxHeader(titleText, safeWidth))

        // 2. Navigation Chrome Always at the Top (Persistently visible across all drill levels)
        val navChrome = when (state.level) {
            DrillLevel.CATEGORIES -> "Keyboard: ↑/↓ Navigate  →/Enter Subcategories  Esc Close"
            DrillLevel.SUBCATEGORIES -> "Keyboard: ← Back  ↑/↓ Navigate  →/Enter Commands  Esc Close"
            DrillLevel.COMMANDS -> "Keyboard: ← Back  ↑/↓ Navigate  → Inspect Card  Enter Select  Esc Close"
            DrillLevel.LEAF_DOCS -> "Keyboard: ← Back to Commands  Enter Select Command  Esc Close"
        }
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, navChrome.padEnd(safeWidth - 4)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        // 3. Persistent MOST USED Header (on root categories level)
        if (state.level == DrillLevel.CATEGORIES) {
            val topUsed = rankingStore.topMostUsed(3)
            if (topUsed.isNotEmpty()) {
                val mostUsedLine = "MOST USED: " + topUsed.joinToString("  ") { cmd ->
                    theme.paint(Role.ACCENT_FOCUS, cmd)
                }
                lines.add("│ " + TerminalText.ellipsize(mostUsedLine, safeWidth - 4).padEnd(safeWidth - 4) + " │")
                lines.add("├" + "─".repeat(safeWidth - 2) + "┤")
            }
        }

        // 4. Content Area Based on DrillLevel
        when (state.level) {
            DrillLevel.CATEGORIES -> renderCategoriesLevel(lines, state, safeWidth)
            DrillLevel.SUBCATEGORIES -> renderSubcategoriesLevel(lines, state, safeWidth)
            DrillLevel.COMMANDS -> renderCommandsLevel(lines, state, safeWidth)
            DrillLevel.LEAF_DOCS -> renderLeafDocLevel(lines, state, safeWidth)
        }

        // 5. Box Bottom Border
        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    /**
     * Level 0: 8 Canonical Categories from Android APK.
     */
    fun renderCategoriesLevel(lines: MutableList<String>, state: TrieNavState, width: Int) {
        val categories = listOf(
            CategoryMeta("agent", "Autonomous Agent & Goals", "⚡", 9),
            CategoryMeta("providers", "Providers & Neural Cascades", "🧠", 7),
            CategoryMeta("code", "AST Parsing & Self-Healing", "🔧", 7),
            CategoryMeta("workspace", "Workspace, Territory & Files", "📁", 7),
            CategoryMeta("git", "Git & Version Control", "🌿", 6),
            CategoryMeta("mcp", "MCP Servers & Tool Governance", "🔌", 6),
            CategoryMeta("diagnostics", "Telemetry, Doctor & HUD", "📊", 6),
            CategoryMeta("specgraph", "SpecGraph & Merkle Sealing", "📜", 5)
        )
        val innerWidth = width - 4

        categories.forEachIndexed { index, cat ->
            val isSelected = index == state.selectedIndex ||
                (state.selectedIndex !in categories.indices && cat.id == state.selectedCategory)
            val cursor = if (isSelected) "❯ " else "  "
            val glyph = cat.glyph
            val prefix = "$cursor$glyph /${cat.id}".padEnd(18)
            val prefixStyled = if (isSelected) theme.paint(Role.ACCENT_FOCUS, prefix) else theme.paint(Role.TEXT_PRIMARY, prefix)

            val countNote = " [${cat.commandCount} cmds]"
            val desc = cat.title + countNote
            val descStyled = if (isSelected) theme.strong(desc) else theme.subdued(desc)
            val row = "$prefixStyled $descStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }
    }

    data class CategoryMeta(val id: String, val title: String, val glyph: String, val commandCount: Int)

    /**
     * Level 1: Subcategories under selected Category.
     */
    fun renderSubcategoriesLevel(lines: MutableList<String>, state: TrieNavState, width: Int) {
        val domainNode = taxonomy.root[state.selectedCategory]
        val innerWidth = width - 4
        val subcategories = domainNode?.children?.keys?.toList() ?: emptyList()

        lines.add("│ " + theme.paint(Role.BRAND, "CATEGORY: /${state.selectedCategory} — Select Subcategory".padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(width - 2) + "┤")

        if (subcategories.isEmpty()) {
            lines.add("│ " + theme.subdued("No subcategories found. Press Enter to view commands.".padEnd(innerWidth)) + " │")
            return
        }

        subcategories.forEachIndexed { index, subcat ->
            val isSelected = index == state.selectedIndex
            val cursor = if (isSelected) "❯ " else "  "
            val subNode = domainNode?.children?.get(subcat)
            val cmdCount = subNode?.children?.size ?: 0
            val subStyled = if (isSelected) theme.paint(Role.ACCENT_FOCUS, subcat.padEnd(24)) else theme.paint(Role.TEXT_PRIMARY, subcat.padEnd(24))
            val detail = "[$cmdCount commands] ${subNode?.description ?: ""}"
            val detailStyled = if (isSelected) theme.strong(detail) else theme.subdued(detail)
            val row = "$cursor$subStyled $detailStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }
    }

    /**
     * Level 2: Commands under selected Subcategory.
     */
    fun renderCommandsLevel(lines: MutableList<String>, state: TrieNavState, width: Int) {
        val domainNode = taxonomy.root[state.selectedCategory]
        val innerWidth = width - 4

        val commands = if (state.selectedSubcategory != null) {
            domainNode?.children?.get(state.selectedSubcategory)?.children?.entries?.toList() ?: emptyList()
        } else {
            domainNode?.children?.values?.flatMap { it.children.entries }?.toList() ?: emptyList()
        }

        val headerText = "Category: /${state.selectedCategory} > ${state.selectedSubcategory ?: "All Commands"}"
        lines.add("│ " + theme.paint(Role.BRAND, headerText.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(width - 2) + "┤")

        if (commands.isEmpty()) {
            lines.add("│ " + theme.subdued("No commands found.".padEnd(innerWidth)) + " │")
            return
        }

        commands.forEachIndexed { index, (cmd, node) ->
            val isSelected = index == state.selectedIndex
            val cursor = if (isSelected) "❯ " else "  "
            val cmdStyled = if (isSelected) theme.paint(Role.ACCENT_FOCUS, cmd.padEnd(20)) else theme.paint(Role.TEXT_PRIMARY, cmd.padEnd(20))
            val desc = node.leafResolution ?: node.description
            val descStyled = if (isSelected) theme.strong(desc) else theme.subdued(desc)
            val row = "$cursor$cmdStyled $descStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }
    }

    /**
     * Level 3: Detailed Progressive Command Card.
     */
    fun renderLeafDocLevel(lines: MutableList<String>, state: TrieNavState, width: Int) {
        val innerWidth = width - 4
        val cmdName = state.selectedCommand ?: "/"
        val leaf = CommandLeafRegistry.getOrDefault(cmdName)

        val renderedLeaf = CommandLeafRegistry.render(leaf, isExpanded = true, theme = theme, width = innerWidth)
        renderedLeaf.forEach { line ->
            lines.add("│ " + TerminalText.padEnd(line, innerWidth) + " │")
        }
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, "  [Press Enter to inject command to buffer without executing]".padEnd(innerWidth)) + " │")
    }

    // Backwards compatibility delegates for domain level
    fun renderDomainLevel(lines: MutableList<String>, state: TrieNavState, width: Int) =
        renderCategoriesLevel(lines, state, width)

    fun renderSubcategoryOrActionLevel(lines: MutableList<String>, state: TrieNavState, width: Int) =
        renderCommandsLevel(lines, state, width)

    fun renderBoxHeader(title: String, width: Int): String {
        val cleanTitle = " $title "
        val remaining = (width - cleanTitle.length - 2).coerceAtLeast(2)
        val left = remaining / 2
        val right = remaining - left
        return "╭" + "─".repeat(left) + cleanTitle + "─".repeat(right) + "╮"
    }

    /**
     * Returns ANSI escape sequence for zero-scrollback floating rendering.
     */
    fun renderFloating(state: TrieNavState, width: Int): String {
        val lines = render(state, width)
        return AnsiFloatingOverlay.composeFloatingBox(lines)
    }

    /**
     * Returns ANSI escape sequence for erasing the floating box completely.
     */
    fun clearFloating(lineCount: Int): String =
        AnsiFloatingOverlay.composeEraseBox(lineCount)

    /**
     * Directly outputs floating box to target PrintStream without println().
     */
    fun drawFloating(state: TrieNavState, width: Int, out: PrintStream = System.out): Int {
        val lines = render(state, width)
        val ansi = AnsiFloatingOverlay.composeFloatingBox(lines)
        out.print(ansi)
        out.flush()
        return lines.size
    }

    /**
     * Directly erases the floating box from the terminal screen.
     */
    fun eraseFloating(lineCount: Int, out: PrintStream = System.out) {
        val ansi = AnsiFloatingOverlay.composeEraseBox(lineCount)
        out.print(ansi)
        out.flush()
    }
}
