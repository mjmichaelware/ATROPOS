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

            val label = TerminalText.clip(tab.name, if (isNarrow) 10 else 20)
            val body = "$trustIcon $label"

            // The active tab is the one thing this row exists to tell you, so
            // it is inverted rather than marked with a character the eye has
            // to hunt for among the separators.
            if (tab.isActive) theme.selection(" $body ") else theme.subdued(" $body ")
        }

        // Padded to the full width: a bar that stopped at its content left the
        // frame's own background showing through, which read as a gap in the
        // chrome rather than as the end of the tabs.
        val tabLine = TerminalText.padEnd(
            TerminalText.ellipsize(rendered.joinToString(theme.subdued("│")), safeWidth),
            safeWidth
        )
        val territoryLine = territories.firstOrNull()?.let {
            territoryMaterial.render(it, safeWidth)
        }
        return listOfNotNull(tabLine, territoryLine)
    }
}
