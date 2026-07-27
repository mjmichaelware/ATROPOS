/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class StatusLineRenderer(
    private val theme: TerminalTheme
) {
    fun render(
        provider: String,
        mode: String,
        workspace: String,
        tracker: QuotaSessionTracker?,
        width: Int
    ): String {
        val tokens = tracker
            ?.estimatedTokens
            ?.takeIf { it > 0 }
            ?.toString()
            ?: "--"

        val cost = tracker
            ?.estimatedCostUsd()
            ?.takeIf { it > 0.0 }
            ?.let { "$" + String.format("%.4f", it) }
            ?: "--"

        val plain = listOf(
            provider.lowercase(),
            mode.lowercase(),
            "$tokens tok",
            cost,
            TerminalText.compactPath(workspace)
        ).joinToString(" · ")

        return theme.metadata(
            TerminalText.ellipsize(
                plain,
                width.coerceAtLeast(36)
            )
        )
    }
}
