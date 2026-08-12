package atropos.core.hr

import atropos.core.security.RedactionFilter
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

class HrRouterService(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val auditStore: HrRouterAuditStore = HrRouterAuditStore(),
    private val auditLog: MutableList<HrRouterAuditEntry> =
        auditStore.list(MAX_IN_MEMORY_AUDIT_ENTRIES).toMutableList()
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
            HrRouteAction.APPROVED -> redactionFilter.redact(request.query).take(MAX_RELEASE_CHARS)
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
            classification = request.classification,
            sourceRole = request.sourceRole,
            targetRole = request.targetRole,
            risk = risk, approved = approved, action = action, reason = reason,
            taskId = request.taskId,
            sourceCoordinates = request.sourceCoordinates.take(20).map(redactionFilter::redact),
            needToKnowSha256 = request.needToKnow.takeIf { it.isNotBlank() }?.let(::sha256),
            requestedPaths = request.requestedPaths.take(20).map(redactionFilter::redact),
            timestamp = Instant.now()
        )
        auditLog += entry
        if (auditLog.size > MAX_IN_MEMORY_AUDIT_ENTRIES) {
            auditLog.subList(0, auditLog.size - MAX_IN_MEMORY_AUDIT_ENTRIES).clear()
        }
        auditStore.append(entry)

        return CrossBoundaryResponse(
            requestId = request.id, approved = approved,
            redactedContent = redacted, risk = risk, classification = request.classification, action = action,
            reason = when (action) {
                HrRouteAction.APPROVED -> "content redacted and released"
                HrRouteAction.NARROWED -> "content narrowed, redacted, and released"
                HrRouteAction.ESCALATED -> "cross-boundary request escalated to Human Owner"
                HrRouteAction.DENIED -> "cross-boundary request denied: $risk risk"
            }
        )
    }

    fun request(
        sourceOwner: String,
        sourceTerr: String,
        targetOwner: String,
        targetTerr: String,
        kind: InformationKind,
        query: String,
        paths: List<String> = emptyList(),
        taskId: String = "",
        sourceCoordinates: List<String> = emptyList(),
        needToKnow: String = "",
        sourceRole: atropos.core.hierarchy.HierarchyRole? = null,
        targetRole: atropos.core.hierarchy.HierarchyRole? = null
    ): CrossBoundaryResponse {
        val request = CrossBoundaryRequest(
            sourceOwnerId = sourceOwner, sourceTerritoryId = sourceTerr,
            targetOwnerId = targetOwner, targetTerritoryId = targetTerr,
            kind = kind, query = query, taskId = taskId,
            sourceCoordinates = sourceCoordinates, needToKnow = needToKnow,
            requestedPaths = paths, sourceRole = sourceRole, targetRole = targetRole
        )
        return route(request)
    }

    fun assessRisk(request: CrossBoundaryRequest): CrossBoundaryRisk {
        val loweredQuery = request.query.lowercase(Locale.US)
        val hasSecretKeywords = secretKeywords.any { keyword ->
            secretWordPattern(keyword).containsMatchIn(loweredQuery)
        }
        val hasSecretPaths = request.requestedPaths.any { path ->
            val loweredPath = path.lowercase(Locale.US)
            containsSecretKeyword(loweredPath)
        }
        val hasCredentials = request.requestedPaths.any { path ->
            val loweredPath = path.lowercase(Locale.US)
            loweredPath.contains(".env") || loweredPath.contains("credentials") || loweredPath.contains("token.json")
        }
        val isHighRiskTerritory = request.targetTerritoryId.lowercase(Locale.US).let { territory ->
            territory.contains("secret") || territory.contains("security")
        }
        val missingBoundaryIdentity = listOf(
            request.sourceOwnerId,
            request.sourceTerritoryId,
            request.targetOwnerId,
            request.targetTerritoryId,
            request.query
        ).any(String::isBlank)
        val missingSourceCoordinates = request.classification == InformationClassification.RESTRICTED &&
            (request.sourceCoordinates.isEmpty() || request.sourceCoordinates.any(String::isBlank))
        val restrictedRoleDenied = request.classification == InformationClassification.RESTRICTED &&
            request.targetRole !in setOf(
                atropos.core.hierarchy.HierarchyRole.HUMAN_OWNER,
                atropos.core.hierarchy.HierarchyRole.DIRECTOR,
                atropos.core.hierarchy.HierarchyRole.DIVISION_VP,
                atropos.core.hierarchy.HierarchyRole.AUDITOR
            )

        return when {
            missingBoundaryIdentity || missingSourceCoordinates -> CrossBoundaryRisk.CRITICAL
            restrictedRoleDenied -> CrossBoundaryRisk.CRITICAL
            isHighRiskTerritory || hasCredentials -> CrossBoundaryRisk.CRITICAL
            hasSecretKeywords || hasSecretPaths -> CrossBoundaryRisk.HIGH
            request.kind == InformationKind.CREDENTIAL_REFERENCE || request.kind == InformationKind.CONFIGURATION -> CrossBoundaryRisk.MEDIUM
            effectiveContextSize(request) > MAX_CONTEXT_CHARS -> CrossBoundaryRisk.MEDIUM
            else -> CrossBoundaryRisk.LOW
        }
    }

    fun auditLog(limit: Int = 100): List<HrRouterAuditEntry> {
        val boundedLimit = limit.coerceIn(1, MAX_AUDIT_QUERY_ENTRIES)
        return auditStore.list(boundedLimit).ifEmpty { auditLog.takeLast(boundedLimit) }
    }

    fun auditSummary(): String {
        val entries = auditLog()
        val approved = entries.count { it.approved }
        val denied = entries.count { !it.approved }
        return "HR Router: $approved approved, $denied denied, ${entries.size} total"
    }

    private fun narrow(request: CrossBoundaryRequest): String {
        val allowedPaths = request.requestedPaths
            .filterNot { path ->
                containsSecretKeyword(path) || path.contains(".env", ignoreCase = true)
            }
            .take(5)
        val query = redactionFilter.redact(request.query)
            .lineSequence()
            .filterNot(::containsSecretKeyword)
            .take(20)
            .joinToString("\n")
            .ifBlank { "request narrowed: sensitive content withheld" }
        return buildString {
            append(query)
            if (allowedPaths.isNotEmpty()) append("\npaths=").append(allowedPaths.joinToString(","))
        }.take(2000)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun secretWordPattern(keyword: String): Regex =
        Regex("(?:^|[^a-z0-9])${Regex.escape(keyword)}(?:$|[^a-z0-9])")

    private fun containsSecretKeyword(value: String): Boolean =
        secretKeywords.any { secretWordPattern(it).containsMatchIn(value.lowercase(Locale.US)) }

    private fun effectiveContextSize(request: CrossBoundaryRequest): Long {
        val pathChars = request.requestedPaths.sumOf { it.length.toLong().coerceAtMost(MAX_CONTEXT_CHARS.toLong()) }
        return maxOf(request.contextSize.toLong(), request.query.length.toLong(), pathChars)
    }

    private companion object {
        const val MAX_IN_MEMORY_AUDIT_ENTRIES = 10_000
        const val MAX_AUDIT_QUERY_ENTRIES = 5_000
        const val MAX_CONTEXT_CHARS = 50_000
        const val MAX_RELEASE_CHARS = 2_000
    }
}
