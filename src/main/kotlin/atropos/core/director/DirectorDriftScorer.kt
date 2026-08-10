package atropos.core.director

/**
 * Deterministic severity-to-deviation mapping for Director observations.
 *
 * The score is advisory telemetry, not a promotion decision. Promotion still
 * uses the explicit blocking kinds and severities in DirectorService.
 */
object DirectorDriftScorer {
    fun score(kind: ObservationKind, severity: DriftSeverity): Int {
        val severityWeight = when (severity) {
            DriftSeverity.INFO -> 10
            DriftSeverity.ADVISORY -> 30
            DriftSeverity.WARNING -> 60
            DriftSeverity.CRITICAL -> 100
        }
        val kindWeight = when (kind) {
            ObservationKind.TERRITORY_VIOLATION,
            ObservationKind.POLICY_VIOLATION,
            ObservationKind.MISSING_GATE,
            ObservationKind.COMPILE_ERROR -> 10
            ObservationKind.STALE_LEASE,
            ObservationKind.FAILURE_RATE,
            ObservationKind.MEMORY_WATERMARK,
            ObservationKind.DIFF_DRIFT -> 0
        }
        return (severityWeight + kindWeight).coerceAtMost(100)
    }
}
