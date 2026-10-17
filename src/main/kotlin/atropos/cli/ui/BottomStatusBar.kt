/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * ATOM-CLI-011: Top-Bar Eradication & Dynamic ANSI Bottom Bar.
 *
 * Emits a single unified bottom status bar inheriting the terminal ANSI palette.
 * Replaces the top-edge status bar so visual element count at top edge = 0.
 * The bar width exactly matches the terminal column width.
 */
class BottomStatusBar(private val theme: TerminalTheme) {

    fun render(
        workspacePath: String,
        branch: String,
        provider: String,
        progressPercent: Int,
        costUsd: Double,
        width: Int
    ): String {
        val safeWidth = width.coerceAtLeast(20)
        val compactPath = TerminalText.compactPath(workspacePath)
        val pathStyled = theme.paint(Role.PATH, compactPath)

        val branchStyled = if (branch.isNotBlank()) {
            val sanitized = TerminalText.sanitize(branch)
            theme.paint(Role.TEXT_SECONDARY, "⎇ $sanitized")
        } else {
            ""
        }

        val providerName = provider.ifBlank { "atropos" }.lowercase()
        val providerStyled = theme.paint(Role.BRAND, "◆ $providerName")

        val progressStyled = if (progressPercent in 0..100) {
            theme.paint(Role.STATUS_RUNNING, "$progressPercent%")
        } else {
            ""
        }

        val costStyled = if (costUsd >= 0.0) {
            val formattedCost = String.format(java.util.Locale.US, "$%.4f", costUsd)
            theme.paint(Role.STATUS_VERIFIED, formattedCost)
        } else {
            ""
        }

        val leftTokens = listOf(pathStyled, branchStyled).filter { it.isNotBlank() }
        val left = leftTokens.joinToString(" ")

        val rightTokens = listOf(providerStyled, progressStyled, costStyled).filter { it.isNotBlank() }
        val right = rightTokens.joinToString(" │ ")

        val leftWidth = TerminalText.cellWidth(left)
        val rightWidth = TerminalText.cellWidth(right)
        val totalWidth = leftWidth + rightWidth

        return if (totalWidth + 1 <= safeWidth) {
            val spaceCount = (safeWidth - totalWidth).coerceAtLeast(1)
            val padding = " ".repeat(spaceCount)
            theme.paint(Role.SURFACE_FOOTER, TerminalText.padEnd(left + padding + right, safeWidth))
        } else {
            val availableLeft = (safeWidth - rightWidth - 2).coerceAtLeast(5)
            val truncatedLeft = TerminalText.ellipsize(left, availableLeft)
            val remainingSpace = (safeWidth - TerminalText.cellWidth(truncatedLeft) - rightWidth).coerceAtLeast(1)
            val line = truncatedLeft + " ".repeat(remainingSpace) + right
            theme.paint(Role.SURFACE_FOOTER, TerminalText.padEnd(TerminalText.ellipsize(line, safeWidth), safeWidth))
        }
    }
}
