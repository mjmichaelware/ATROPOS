package atropos.core.hr

import atropos.core.security.RedactionFilter
import java.time.Instant
import java.util.UUID

enum class CrossBoundaryRisk { LOW, MEDIUM, HIGH, CRITICAL }

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
    val timestamp: Instant = Instant.now()
)

data class HrRouterAuditEntry(
    val requestId: String,
    val sourceOwnerId: String,
    val targetOwnerId: String,
    val kind: InformationKind,
    val risk: CrossBoundaryRisk,
    val approved: Boolean,
    val reason: String,
    val timestamp: Instant
)

class HrRouterService(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val auditLog: MutableList<HrRouterAuditEntry> = mutableListOf()
) {
    private val secretKeywords = listOf("token", "secret", "password", "key", "credential", "auth", "bearer")

    fun route(request: CrossBoundaryRequest): CrossBoundaryResponse {
        val risk = assessRisk(request)
        val approved = risk.ordinal < CrossBoundaryRisk.CRITICAL.ordinal
        val redacted = if (approved) {
            redactionFilter.redact(request.query)
        } else null

        auditLog += HrRouterAuditEntry(
            requestId = request.id, sourceOwnerId = request.sourceOwnerId,
            targetOwnerId = request.targetOwnerId, kind = request.kind,
            risk = risk, approved = approved, reason = if (approved) "approved with redaction" else "denied: risk=$risk",
            timestamp = Instant.now()
        )

        return CrossBoundaryResponse(
            requestId = request.id, approved = approved,
            redactedContent = redacted, risk = risk,
            reason = if (approved) "content redacted and released" else "cross-boundary request denied: $risk risk"
        )
    }

    fun request(sourceOwner: String, sourceTerr: String, targetOwner: String, targetTerr: String, kind: InformationKind, query: String, paths: List<String> = emptyList()): CrossBoundaryResponse {
        val request = CrossBoundaryRequest(
            sourceOwnerId = sourceOwner, sourceTerritoryId = sourceTerr,
            targetOwnerId = targetOwner, targetTerritoryId = targetTerr,
            kind = kind, query = query, requestedPaths = paths
        )
        return route(request)
    }

    fun assessRisk(request: CrossBoundaryRequest): CrossBoundaryRisk {
        val loweredQuery = request.query.lowercase()
        val hasSecretKeywords = secretKeywords.any { loweredQuery.contains(it) }
        val hasSecretPaths = request.requestedPaths.any { p -> secretKeywords.any { p.lowercase().contains(it) } }
        val hasCredentials = request.requestedPaths.any { it.contains(".env") || it.contains("credentials") || it.contains("token.json") }
        val isHighRiskTerritory = request.targetTerritoryId.contains("secret") || request.targetTerritoryId.contains("security")

        return when {
            isHighRiskTerritory || hasCredentials -> CrossBoundaryRisk.CRITICAL
            hasSecretKeywords || hasSecretPaths -> CrossBoundaryRisk.HIGH
            request.kind == InformationKind.CREDENTIAL_REFERENCE || request.kind == InformationKind.CONFIGURATION -> CrossBoundaryRisk.MEDIUM
            request.contextSize > 50_000 -> CrossBoundaryRisk.MEDIUM
            else -> CrossBoundaryRisk.LOW
        }
    }

    fun auditLog(limit: Int = 100): List<HrRouterAuditEntry> = auditLog.takeLast(limit)

    fun auditSummary(): String {
        val approved = auditLog.count { it.approved }
        val denied = auditLog.count { !it.approved }
        return "HR Router: $approved approved, $denied denied, ${auditLog.size} total"
    }
}
