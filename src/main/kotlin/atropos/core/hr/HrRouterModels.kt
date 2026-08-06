package atropos.core.hr

import java.time.Instant
import java.util.UUID

enum class CrossBoundaryRisk { LOW, MEDIUM, HIGH, CRITICAL }

enum class HrRouteAction { APPROVED, NARROWED, DENIED, ESCALATED }

enum class InformationKind {
    SOURCE_CODE,
    DEPENDENCY,
    CONFIGURATION,
    CREDENTIAL_REFERENCE,
    TERRITORY_METADATA,
    TASK_ASSIGNMENT,
    VERIFICATION_RESULT,
    MEMORY_QUERY,
    PROVIDER_ROUTE
}

data class CrossBoundaryRequest(
    val id: String = "hr-${UUID.randomUUID().toString().take(12)}",
    val sourceOwnerId: String,
    val sourceTerritoryId: String,
    val targetOwnerId: String,
    val targetTerritoryId: String,
    val kind: InformationKind,
    val query: String,
    val taskId: String = "",
    val sourceCoordinates: List<String> = emptyList(),
    val needToKnow: String = "",
    val contextSize: Int = 0,
    val requestedPaths: List<String> = emptyList(),
    val timestamp: Instant = Instant.now()
)

data class CrossBoundaryResponse(
    val id: String = "hr-resp-${UUID.randomUUID().toString().take(12)}",
    val requestId: String,
    val approved: Boolean,
    val redactedContent: String? = null,
    val risk: CrossBoundaryRisk = CrossBoundaryRisk.LOW,
    val reason: String,
    val action: HrRouteAction = if (approved) HrRouteAction.APPROVED else HrRouteAction.DENIED,
    val timestamp: Instant = Instant.now()
)

data class HrRouterAuditEntry(
    val requestId: String,
    val sourceOwnerId: String,
    val sourceTerritoryId: String = "",
    val targetOwnerId: String,
    val targetTerritoryId: String = "",
    val kind: InformationKind,
    val risk: CrossBoundaryRisk,
    val approved: Boolean,
    val action: HrRouteAction = if (approved) HrRouteAction.APPROVED else HrRouteAction.DENIED,
    val reason: String,
    val taskId: String = "",
    val sourceCoordinates: List<String> = emptyList(),
    val needToKnowSha256: String? = null,
    val requestedPaths: List<String> = emptyList(),
    val timestamp: Instant
)
