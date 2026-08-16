package atropos.core.agent

data class BatchDelta(
    val changedPaths: Set<String>,
    val unauthorizedPaths: Set<String>
) {
    val isZero: Boolean get() = unauthorizedPaths.isEmpty()
}

data class BatchGateDecision(
    val passed: Boolean,
    val delta: BatchDelta,
    val reason: String,
    val higValue: Double = 0.0,
    val hudValue: Double = 0.0
)

/** Enforces E(DELTA)=0 for changes outside the declared batch territory. */
class BatchGate {
    fun evaluate(
        before: Map<String, String>,
        after: Map<String, String>,
        declaredTerritory: Set<String>,
        higValue: Double = 0.0,
        hudValue: Double = 0.0
    ): BatchGateDecision {
        val changed = (before.keys + after.keys).filterTo(sortedSetOf()) { path ->
            before[path] != after[path]
        }
        val normalizedTerritory = declaredTerritory.map(::normalize).toSet()
        val unauthorized = changed.filterTo(sortedSetOf()) { path ->
            val normalized = normalize(path)
            normalizedTerritory.none { allowed ->
                normalized == allowed || normalized.startsWith("$allowed/")
            }
        }
        val delta = BatchDelta(changed, unauthorized)
        val finiteZero = higValue.isFinite() && hudValue.isFinite() &&
            (higValue + hudValue) == 0.0
        return BatchGateDecision(
            passed = delta.isZero && finiteZero,
            delta = delta,
            reason = if (!delta.isZero) {
                "batch delta escaped territory: ${unauthorized.joinToString(", ")}"
            } else if (!finiteZero) {
                "E(DELTA) refused: HIG=$higValue HUD=$hudValue must be finite and sum to zero"
            } else {
                "batch delta authorized paths=${changed.size}"
            },
            higValue = higValue,
            hudValue = hudValue
        )
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }.joinToString("/")
}
