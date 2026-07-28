package atropos.core.hr

import atropos.core.security.RedactionFilter
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
    val targetOwnerId: String,
    val kind: InformationKind,
    val risk: CrossBoundaryRisk,
    val approved: Boolean,
    val action: HrRouteAction = if (approved) HrRouteAction.APPROVED else HrRouteAction.DENIED,
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
        val action = when (risk) {
            CrossBoundaryRisk.LOW -> HrRouteAction.APPROVED
            CrossBoundaryRisk.MEDIUM -> HrRouteAction.ESCALATED
            CrossBoundaryRisk.HIGH -> HrRouteAction.NARROWED
            CrossBoundaryRisk.CRITICAL -> HrRouteAction.DENIED
        }
        val approved = action == HrRouteAction.APPROVED || action == HrRouteAction.NARROWED
        val redacted = when (action) {
            HrRouteAction.APPROVED -> redactionFilter.redact(request.query)
            HrRouteAction.NARROWED -> narrow(request)
            HrRouteAction.DENIED,
            HrRouteAction.ESCALATED -> null
        }
        val reason = when (action) {
            HrRouteAction.APPROVED -> "approved with redaction"
            HrRouteAction.NARROWED -> "approved with narrowing and redaction"
            HrRouteAction.ESCALATED -> "escalated to Human Owner: risk=$risk"
            HrRouteAction.DENIED -> "denied: risk=$risk"
        }

        auditLog += HrRouterAuditEntry(
            requestId = request.id, sourceOwnerId = request.sourceOwnerId,
            targetOwnerId = request.targetOwnerId, kind = request.kind,
            risk = risk, approved = approved, action = action, reason = reason,
            timestamp = Instant.now()
        )

        return CrossBoundaryResponse(
            requestId = request.id, approved = approved,
            redactedContent = redacted, risk = risk, action = action,
            reason = when (action) {
                HrRouteAction.APPROVED -> "content redacted and released"
                HrRouteAction.NARROWED -> "content narrowed, redacted, and released"
                HrRouteAction.ESCALATED -> "cross-boundary request escalated to Human Owner"
                HrRouteAction.DENIED -> "cross-boundary request denied: $risk risk"
            }
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

    private fun narrow(request: CrossBoundaryRequest): String {
        val allowedPaths = request.requestedPaths
            .filterNot { path -> secretKeywords.any { path.lowercase().contains(it) } || path.contains(".env") }
            .take(5)
        val query = redactionFilter.redact(request.query)
            .lineSequence()
            .filterNot { line -> secretKeywords.any { line.lowercase().contains(it) } }
            .take(20)
            .joinToString("\n")
            .ifBlank { "request narrowed: sensitive content withheld" }
        return buildString {
            append(query)
            if (allowedPaths.isNotEmpty()) append("\npaths=").append(allowedPaths.joinToString(","))
        }.take(2000)
    }
}
