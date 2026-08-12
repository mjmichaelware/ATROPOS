package atropos.cli.ui

/** One-line provider health summary with a deterministic width budget. */
class ProviderOneLineSummary {
    fun render(active: String, health: ProviderSummaryRenderer.ProviderHealth, width: Int): String {
        val safeWidth = width.coerceAtLeast(40)
        val icon = when (health.status) {
            "healthy" -> "●"
            "degraded" -> "◑"
            else -> "○"
        }
        val cost = if (health.costUsd > 0.01) " \$${String.format("%.3f", health.costUsd)}" else ""
        val quota = if (health.quotaPercent > 80) " ${health.quotaPercent}%" else ""
        return "$icon ${active.ifBlank { health.name }}$cost$quota".take(safeWidth).padEnd(safeWidth)
    }
}
