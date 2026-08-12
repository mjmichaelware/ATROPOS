/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.territory.TerritoryAssignment

/**
 * HOE-B02: Session/tab bar with OpenCode parity.
 * Shows active tab + count; collapses on narrow terminals.
 * Trust indicator (●/◯) per tab.
 */
class SessionTabBar(private val theme: TerminalTheme) {
    private val territoryMaterial = TerritoryAsMaterial()

    fun render(
        tabs: List<ViewportLayout.TabState>,
        width: Int,
        territories: List<TerritoryAssignment> = emptyList()
    ): List<String> {
        if (tabs.isEmpty()) return emptyList()

        val safeWidth = width.coerceAtLeast(20)
        val isNarrow = safeWidth < 60

        val rendered = tabs.map { tab ->
            val trustIcon = when (tab.trustLevel) {
                ViewportLayout.TrustIndicator.ATTESTED -> "●"
                ViewportLayout.TrustIndicator.UNATTESTED -> "○"
                ViewportLayout.TrustIndicator.UNKNOWN -> "?"
            }

            val marker = if (tab.isActive) "┃" else " "
            val label = tab.name.take(if (isNarrow) 10 else 20)

            "$marker $trustIcon $label"
        }

        val tabLine = rendered.joinToString(" │ ").take(safeWidth).padEnd(safeWidth)
        val territoryLine = territories.firstOrNull()?.let {
            territoryMaterial.render(it, safeWidth)
        }
        return listOfNotNull("┌$tabLine┐".take(safeWidth), territoryLine)
    }
}
