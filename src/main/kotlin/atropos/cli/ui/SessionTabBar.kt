/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.core.territory.TerritoryAssignment

/**
 * HOE-B02: Session/tab bar with OpenCode parity.
 * Shows active tab + count; collapses on narrow terminals.
 * Trust indicator (●/◯) per tab.
 */
class SessionTabBar(private val theme: TerminalTheme) {
    private val territoryMaterial = TerritoryAsMaterial()

    /**
     * Tabs drawn as tabs.
     *
     * Two rows: the box tops, then the labels. A single inverted row was a
     * highlighted word, not a tab -- and the top of the screen already said
     * "tab 1" in plain text while the footer said it again, so the same fact
     * appeared twice and looked like a tab neither time.
     *
     * The active tab is the only one drawn closed; the rest are dimmed. That
     * is the whole job of a tab strip, so it is the thing the rendering spends
     * its contrast on.
     */
    fun render(
        tabs: List<ViewportLayout.TabState>,
        width: Int,
        territories: List<TerritoryAssignment> = emptyList()
    ): List<String> {
        if (tabs.isEmpty()) return emptyList()

        val safeWidth = width.coerceAtLeast(20)
        val labelCells = if (safeWidth < 60) NARROW_LABEL_CELLS else WIDE_LABEL_CELLS

        val tops = StringBuilder()
        val labels = StringBuilder()
        var used = 0

        for (tab in tabs) {
            val trust = when (tab.trustLevel) {
                ViewportLayout.TrustIndicator.ATTESTED -> "●"
                ViewportLayout.TrustIndicator.UNATTESTED -> "○"
                ViewportLayout.TrustIndicator.UNKNOWN -> "·"
            }
            val label = " $trust " + TerminalText.clip(tab.name, labelCells) + " "
            val span = TerminalText.cellWidth(label) + 2

            // Stop before overflowing rather than clipping mid-box: half a tab
            // reads as a rendering fault, where a strip that simply ends reads
            // as a strip that ended.
            if (used + span > safeWidth) break

            if (tab.isActive) {
                tops.append(theme.paint(Role.ACCENT_FOCUS, "╭" + "─".repeat(TerminalText.cellWidth(label)) + "╮"))
                labels.append(theme.paint(Role.ACCENT_FOCUS, "│"))
                    .append(theme.selection(label))
                    .append(theme.paint(Role.ACCENT_FOCUS, "│"))
            } else {
                tops.append(theme.subdued("╭" + "─".repeat(TerminalText.cellWidth(label)) + "╮"))
                labels.append(theme.subdued("│" + label + "│"))
            }
            used += span
        }

        val territoryLine = territories.firstOrNull()?.let {
            territoryMaterial.render(it, safeWidth)
        }

        return listOfNotNull(
            TerminalText.padEnd(tops.toString(), safeWidth),
            TerminalText.padEnd(labels.toString(), safeWidth),
            territoryLine
        )
    }

    private companion object {
        const val NARROW_LABEL_CELLS = 10
        const val WIDE_LABEL_CELLS = 18
    }
}
