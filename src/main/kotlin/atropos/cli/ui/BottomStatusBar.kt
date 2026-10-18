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
        val pathStyled = try { theme.paint(Role.PATH, compactPath) } catch (_: Throwable) { compactPath }

        val branchStyled = if (branch.isNotBlank()) {
            val sanitized = TerminalText.sanitize(branch)
            try { theme.paint(Role.TEXT_SECONDARY, "⎇ $sanitized") } catch (_: Throwable) { "⎇ $sanitized" }
        } else {
            ""
        }

        val providerName = provider.ifBlank { "atropos" }.lowercase()
        val providerStyled = try { theme.paint(Role.BRAND, "◆ $providerName") } catch (_: Throwable) { "◆ $providerName" }

        val progressStyled = if (progressPercent in 0..100) {
            try { theme.paint(Role.STATUS_RUNNING, "$progressPercent%") } catch (_: Throwable) { "$progressPercent%" }
        } else {
            ""
        }

        val costStyled = if (costUsd >= 0.0) {
            val formattedCost = String.format(java.util.Locale.US, "$%.4f", costUsd)
            try { theme.paint(Role.STATUS_VERIFIED, formattedCost) } catch (_: Throwable) { formattedCost }
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

        val rawOutput = if (totalWidth + 1 <= safeWidth) {
            val spaceCount = (safeWidth - totalWidth).coerceAtLeast(1)
            val padding = " ".repeat(spaceCount)
            left + padding + right
        } else {
            val availableLeft = (safeWidth - rightWidth - 2).coerceAtLeast(5)
            val truncatedLeft = TerminalText.ellipsize(left, availableLeft)
            val remainingSpace = (safeWidth - TerminalText.cellWidth(truncatedLeft) - rightWidth).coerceAtLeast(1)
            truncatedLeft + " ".repeat(remainingSpace) + right
        }
        val padded = TerminalText.padEnd(TerminalText.ellipsize(rawOutput, safeWidth), safeWidth)
        return try {
            theme.paint(Role.SURFACE_FOOTER, padded)
        } catch (_: Throwable) {
            try {
                theme.paint(Role.TEXT_MUTED, padded)
            } catch (_: Throwable) {
                padded
            }
        }
    }
}
