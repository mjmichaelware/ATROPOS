package atropos.cli.ui

/**
 * One-line provider health summary with a deterministic width budget.
 *
 * `STRICT-19-ProviderOneLineSummary`: one line, always, regardless of how
 * verbose the provider is.
 *
 * The line leads with what the operator selected, then names what that
 * actually resolved to when the two say different things. Both matter and they
 * are not interchangeable: `/use github_models` routing to `groq` is a fact
 * the operator needs, and a line showing only one of the pair hides a
 * mis-route completely.
 *
 * When one name contains the other — `sonnet` resolving to
 * `claude-3-5-sonnet` — only the resolved form is shown. Repeating a substring
 * of the answer next to the answer spends characters to say nothing.
 *
 * Cost and quota shed first as the width tightens, because both are available
 * in full from `/providers`; the identity pair is not recoverable from
 * anywhere else on screen.
 */
class ProviderOneLineSummary {
    fun render(active: String, health: ProviderSummaryRenderer.ProviderHealth, width: Int): String {
        val safeWidth = width.coerceAtLeast(MINIMUM_WIDTH)
        val icon = when (health.status) {
            "healthy" -> "●"
            "degraded" -> "◑"
            else -> "○"
        }

        val resolved = health.name.ifBlank { active }
        val selected = active.ifBlank { resolved }

        // One name, or both. Both only when neither is a substring of the
        // other, which is what separates "an alias for the same thing" from
        // "a route that landed somewhere else".
        val identity = when {
            selected.equals(resolved, ignoreCase = true) -> resolved
            resolved.contains(selected, ignoreCase = true) -> resolved
            selected.contains(resolved, ignoreCase = true) -> selected
            else -> "$selected ($resolved)"
        }

        val cost = if (health.costUsd > COST_FLOOR) " \$${String.format("%.3f", health.costUsd)}" else ""
        val quota = if (health.quotaPercent > QUOTA_FLOOR) " ${health.quotaPercent}%" else ""

        // Shed until it fits. The identity is truncated rather than dropped --
        // a truncated name still tells the operator they are not on the
        // provider they expected, where an empty line tells them nothing.
        val candidates = listOf(
            "$icon $identity$cost$quota",
            "$icon $identity$cost",
            "$icon $identity"
        )
        val chosen = candidates.firstOrNull { it.length <= safeWidth } ?: candidates.last()

        return chosen.take(safeWidth).padEnd(safeWidth)
    }

    private companion object {
        /**
         * Below this the line cannot carry a model name and a status glyph, so
         * a narrower terminal is treated as this wide and the name truncates
         * rather than the line wrapping into two.
         */
        const val MINIMUM_WIDTH = 40

        /** Costs under a cent are noise on a status line. */
        const val COST_FLOOR = 0.01

        /** Quota is worth a reader's attention only as it runs out. */
        const val QUOTA_FLOOR = 80
    }
}
