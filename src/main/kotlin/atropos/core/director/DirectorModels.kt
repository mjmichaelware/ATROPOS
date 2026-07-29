package atropos.core.director

import java.time.Instant
import java.util.UUID

enum class DriftSeverity { INFO, ADVISORY, WARNING, CRITICAL }

enum class ObservationKind {
    DIFF_DRIFT,
    TERRITORY_VIOLATION,
    STALE_LEASE,
    FAILURE_RATE,
    COMPILE_ERROR,
    MISSING_GATE,
    POLICY_VIOLATION,
    MEMORY_WATERMARK
}

data class DirectorObservation(
    val id: String = "obs-${UUID.randomUUID().toString().take(12)}",
    val kind: ObservationKind,
    val severity: DriftSeverity,
    val source: String,
    val details: String,
    val goalId: String? = null,
    val territoryId: String? = null,
    val filePaths: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val timestamp: Instant = Instant.now(),
    val acknowledged: Boolean = false,
    val dismissed: Boolean = false
)

data class AdvisoryReport(
    val id: String = "adv-${UUID.randomUUID().toString().take(12)}",
    val observations: List<DirectorObservation>,
    val summary: String,
    val timestamp: Instant = Instant.now(),
    val diffHash: String? = null,
    val territoryViolations: Int = 0
)

data class DiffSnapshot(
    val hash: String,
    val changedFiles: List<String>,
    val timestamp: Instant,
    val totalChanges: Int
)

data class DirectorPromotionAdvisory(
    val allowed: Boolean,
    val blockingObservations: List<DirectorObservation>,
    val message: String
)
