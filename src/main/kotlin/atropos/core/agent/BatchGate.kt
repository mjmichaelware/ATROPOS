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
    val reason: String
)

/** Enforces E(DELTA)=0 for changes outside the declared batch territory. */
class BatchGate {
    fun evaluate(
        before: Map<String, String>,
        after: Map<String, String>,
        declaredTerritory: Set<String>
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
        return BatchGateDecision(
            passed = delta.isZero,
            delta = delta,
            reason = if (delta.isZero) {
                "batch delta authorized paths=${changed.size}"
            } else {
                "batch delta escaped territory: ${unauthorized.joinToString(", ")}"
            }
        )
    }

    private fun normalize(path: String): String =
        path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }.joinToString("/")
}
