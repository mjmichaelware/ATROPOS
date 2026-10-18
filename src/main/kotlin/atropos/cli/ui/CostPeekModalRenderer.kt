/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * ATOM-UI-022: Token Cost & Provider Limits Peek Surface ($).
 *
 * Implements the compact modal triggered by `$` showing:
 * - Engine tokens consumed in the current session.
 * - Prompt vs completion token split.
 * - Active provider rate limits and quota fuel cell balance.
 * - Cost tier classification (Free/Local, Standard, Emergency Credits).
 */
class CostPeekModalRenderer(
    private val theme: TerminalTheme
) {
    data class CostMetrics(
        val totalTokensUsed: Long,
        val promptTokens: Long,
        val completionTokens: Long,
        val providerName: String,
        val requestsRemainingMinute: Int = 60,
        val tokensRemainingMinute: Int = 100_000,
        val costClass: String = "FREE_LOCAL",
        val estimatedCostUsd: Double = 0.00
    )

    fun render(
        metrics: CostMetrics,
        width: Int = 60
    ): List<String> {
        val safeWidth = width.coerceAtLeast(50)
        val innerWidth = safeWidth - 4
        val lines = mutableListOf<String>()

        // Box Top
        val title = " ATROPOS — Token Usage & Quota Peek ($) "
        lines.add(renderBoxHeader(title, safeWidth))

        // Navigation
        val nav = "Keyboard: enter / esc Close"
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, nav.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        // Rows
        val rows = listOf(
            "Active Provider" to metrics.providerName,
            "Total Tokens Used" to "%,d".format(metrics.totalTokensUsed),
            "  - Prompt Tokens" to "%,d".format(metrics.promptTokens),
            "  - Output Tokens" to "%,d".format(metrics.completionTokens),
            "Cost Tier" to metrics.costClass,
            "Session Cost (USD)" to "$%.4f".format(metrics.estimatedCostUsd),
            "Rate Limit (RPM)" to "${metrics.requestsRemainingMinute} req/min",
            "Quota Bandwidth (TPM)" to "%,d tok/min".format(metrics.tokensRemainingMinute)
        )

        rows.forEach { (label, value) ->
            val labelCol = theme.paint(Role.TEXT_MUTED, label.padEnd(26))
            val valCol = theme.paint(Role.ACCENT_FOCUS, value)
            val row = "$labelCol : $valCol"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }

        // Box Bottom
        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    private fun renderBoxHeader(title: String, width: Int): String {
        val cleanTitle = " $title "
        val remaining = (width - cleanTitle.length - 2).coerceAtLeast(2)
        val left = remaining / 2
        val right = remaining - left
        return "╭" + "─".repeat(left) + cleanTitle + "─".repeat(right) + "╮"
    }
}
