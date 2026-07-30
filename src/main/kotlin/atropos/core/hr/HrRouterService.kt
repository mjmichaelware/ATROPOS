package atropos.core.hr

import atropos.core.security.RedactionFilter
import java.time.Instant

class HrRouterService(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val auditStore: HrRouterAuditStore = HrRouterAuditStore(),
    private val auditLog: MutableList<HrRouterAuditEntry> = auditStore.list(Int.MAX_VALUE).toMutableList()
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

        val entry = HrRouterAuditEntry(
            requestId = request.id, sourceOwnerId = request.sourceOwnerId,
            sourceTerritoryId = request.sourceTerritoryId,
            targetOwnerId = request.targetOwnerId,
            targetTerritoryId = request.targetTerritoryId,
            kind = request.kind,
            risk = risk, approved = approved, action = action, reason = reason,
            requestedPaths = request.requestedPaths.take(20).map(redactionFilter::redact),
            timestamp = Instant.now()
        )
        auditLog += entry
        auditStore.append(entry)

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

    fun auditLog(limit: Int = 100): List<HrRouterAuditEntry> = auditStore.list(limit).ifEmpty { auditLog.takeLast(limit) }

    fun auditSummary(): String {
        val entries = auditLog()
        val approved = entries.count { it.approved }
        val denied = entries.count { !it.approved }
        return "HR Router: $approved approved, $denied denied, ${entries.size} total"
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
