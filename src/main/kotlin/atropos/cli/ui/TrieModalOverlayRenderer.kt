/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.taxonomy.HierarchicalCommandTaxonomy
import atropos.cli.ui.design.Role

/**
 * ATOM 6.2: Interactive Terminal-Native Category Selection UI.
 *
 * Constructs a terminal-native modal viewport overlay displaying top-level command
 * categories with persistent navigation chrome. Renders keyboard shortcuts visibly
 * at all times across all drill levels.
 *
 * ## Research: Double-Buffered ANSI Escape Sequence Rendering
 * Research double-buffered ANSI escape sequence rendering using alternate screen buffers
 * (\u001b[?1049h / \u001b[?1049l) and synchronized update fences (\u001b[?2026h / \u001b[?2026l)
 * to eliminate visual screen flicker, cursor jump, and tearing during modal state transitions
 * on high-refresh mobile terminal emulators.
 */
class TrieModalOverlayRenderer(
    private val theme: TerminalTheme,
    private val taxonomy: HierarchicalCommandTaxonomy
) {
    enum class DrillLevel {
        DOMAIN,
        SUBCATEGORY,
        ACTION,
        LEAF_INSPECTOR
    }

    data class TrieNavState(
        val level: DrillLevel = DrillLevel.DOMAIN,
        val domainIndex: Int = 0,
        val subcategoryIndex: Int = 0,
        val actionIndex: Int = 0,
        val selectedDomain: String? = null,
        val selectedSubcategory: String? = null,
        val selectedAction: String? = null
    )

    fun render(state: TrieNavState, width: Int, height: Int): List<String> {
        val safeWidth = width.coerceAtLeast(20)
        val safeHeight = height.coerceAtLeast(5)

        return when (state.level) {
            DrillLevel.DOMAIN -> renderDomainLevel(state, safeWidth, safeHeight)
            DrillLevel.SUBCATEGORY, DrillLevel.ACTION -> renderSubcategoryOrActionLevel(state, safeWidth, safeHeight)
            DrillLevel.LEAF_INSPECTOR -> renderLeafInspectorLevel(state, safeWidth, safeHeight)
        }
    }

    fun renderDomainLevel(state: TrieNavState, width: Int, height: Int): List<String> {
        val lines = mutableListOf<String>()
        val header = theme.strong("COMMAND PALETTE — Select Domain")
        val chrome = theme.subdued("[↑/↓ Navigate] [→/Enter Drill In] [Esc Cancel]")
        lines += header
        lines += chrome
        lines += theme.subdued("─".repeat(width.coerceAtMost(60)))

        val domains = taxonomy.root.keys.toList()
        domains.forEachIndexed { index, domain ->
            val isSelected = index == state.domainIndex
            val prefix = if (isSelected) theme.paint(Role.ACCENT_FOCUS, " ▸ ") else "   "
            val item = if (isSelected) theme.strong(domain) else theme.subdued(domain)
            lines += "$prefix$item"
        }

        return lines.take(height)
    }

    fun renderSubcategoryOrActionLevel(state: TrieNavState, width: Int, height: Int): List<String> {
        val lines = mutableListOf<String>()
        val domain = state.selectedDomain ?: "Commands"
        val header = theme.strong("COMMAND PALETTE — $domain")
        val chrome = theme.subdued("[← Back] [↑/↓ Navigate] [→/Enter Select] [Esc Dismiss]")
        lines += header
        lines += chrome
        lines += theme.subdued("─".repeat(width.coerceAtMost(60)))

        val domainNode = taxonomy.root[domain]
        val children = domainNode?.children?.keys?.toList() ?: emptyList()
        children.forEachIndexed { index, child ->
            val isSelected = index == state.actionIndex
            val prefix = if (isSelected) theme.paint(Role.ACCENT_FOCUS, " ▸ ") else "   "
            val label = if (isSelected) theme.strong(child) else theme.subdued(child)
            lines += "$prefix$label"
        }

        return lines.take(height)
    }

    fun renderLeafInspectorLevel(state: TrieNavState, width: Int, height: Int): List<String> {
        val lines = mutableListOf<String>()
        val action = state.selectedAction ?: "Command Details"
        lines += theme.strong("COMMAND SPECIFICATION — $action")
        lines += theme.subdued("[← Back] [Enter Inject to Buffer] [Esc Close]")
        lines += theme.subdued("─".repeat(width.coerceAtMost(60)))
        lines += theme.metadata("Ready for parameter injection.")
        return lines.take(height)
    }
}
