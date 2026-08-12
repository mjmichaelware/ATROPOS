/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * HOE-B06: Collapse provider matrix to one-line healthy summary by default.
 * Full inventory only on /providers full or explicit expand.
 * Status: healthy/degraded/unhealthy; cost/quota shown only when concerning.
 */
class ProviderSummaryRenderer(private val theme: TerminalTheme) {
    data class ProviderHealth(
        val name: String,
        val status: String,  // healthy/degraded/unhealthy
        val costUsd: Double = 0.0,
        val quotaPercent: Int = 0,
        val availableKeys: Int = 0
    )

    fun renderCompact(active: String, fallbacks: List<String>, health: ProviderHealth, width: Int): String {
        val safeWidth = width.coerceAtLeast(40)
        val statusIcon = when (health.status) {
            "healthy" -> "●"
            "degraded" -> "◑"
            else -> "○"
        }

        val costStr = if (health.costUsd > 0.01) " \$${String.format("%.3f", health.costUsd)}" else ""
        val quotaStr = if (health.quotaPercent > 80) " ${health.quotaPercent}%" else ""

        val summary = "$statusIcon ${health.name}$costStr$quotaStr"
        return summary.take(safeWidth).padEnd(safeWidth)
    }

    fun renderFull(providers: List<ProviderHealth>, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val lines = mutableListOf<String>()

        lines.add("Provider Matrix".padEnd(safeWidth))
        providers.forEach { health ->
            val statusIcon = when (health.status) {
                "healthy" -> "●"
                "degraded" -> "◑"
                else -> "○"
            }
            val line = "  $statusIcon ${health.name.padEnd(20)} " +
                    "cost: \$${String.format("%.4f", health.costUsd)} " +
                    "quota: ${health.quotaPercent}% " +
                    "keys: ${health.availableKeys}"
            lines.add(line.take(safeWidth).padEnd(safeWidth))
        }

        return lines
    }
}
