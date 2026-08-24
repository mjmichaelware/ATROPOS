/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.sentry

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.PolicyActionClass
import atropos.core.integration.IntegrationRegistry
import atropos.core.security.DefaultSecretSource
import atropos.core.security.RedactionFilter
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import atropos.core.security.SecretSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class SentryApiRequest(val method: String, val url: String, val token: String)

data class SentryApiWireResponse(val status: Int, val body: String)

fun interface SentryApiTransport {
    fun send(request: SentryApiRequest): SentryApiWireResponse
}

data class SentryApiResponse(val status: Int, val body: String, val evidenceHash: String)

/** The parsed subset required by the issue-to-territory repair path. */
data class SentryIssue(
    val id: String,
    val title: String,
    val culprit: String,
    val frames: List<SentryStackFrame>,
    val raw: String
)

data class SentryStackFrame(val filename: String, val lineNumber: Int?)

/**
 * The sole Sentry network boundary. It is deliberately injectable so tests do
 * not need credentials or a live Sentry deployment. Network admission, secret
 * egress, redaction, and evidence hashing stay owned by existing primitives.
 */
class SentryApiClient(
    private val secretSource: SecretSource = DefaultSecretSource.create(),
    private val gate: (ActionProposal) -> AgencyDecision = BoundedAgencyGate()::evaluate,
    private val transport: SentryApiTransport = ::sendOverHttps,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val baseUrl: String = "https://sentry.io"
) {
    fun getIssue(issueId: String, declaredTerritory: List<String> = listOf(".")): SentryIssue {
        val response = execute("/api/0/issues/${segment(issueId)}/", declaredTerritory)
        require(response.status in 200..299) { "Sentry issue request failed: HTTP ${response.status}" }
        return SentryIssueParser.parse(issueId, response.body)
    }

    fun execute(path: String, declaredTerritory: List<String> = listOf(".")): SentryApiResponse {
        IntegrationRegistry.requireRegistered("sentry")
        val normalizedPath = validatePath(path)
        require(declaredTerritory.isNotEmpty()) { "Sentry request requires declared territory" }
        require(declaredTerritory.all(::validTerritory)) {
            "Sentry request territory must be relative and traversal-free"
        }
        check(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
            "Sentry API refused: SecretSinkMatrix does not permit network credential egress"
        }
        val uri = URI.create(baseUrl.trimEnd('/') + normalizedPath)
        val proposal = ActionProposal(
            id = "sentry-api-${redactionFilter.stableFingerprint(normalizedPath)}",
            actionClass = PolicyActionClass.NETWORK,
            actor = ActionActor.HumanOwner,
            targetPaths = declaredTerritory,
            networkTarget = uri.host,
            metadata = mapOf("integration" to "sentry", "operation" to "read_issue")
        )
        val decision = gate(proposal)
        when (decision.disposition) {
            AgencyDisposition.ALLOWED -> Unit
            AgencyDisposition.APPROVAL_REQUIRED -> error("Sentry API requires explicit approval: ${decision.reason}")
            AgencyDisposition.POLICY_BLOCKED -> error("Sentry API refused by policy: ${decision.reason}")
        }
        val token = listOf("SENTRY_AUTH_TOKEN", "SENTRY_TOKEN")
            .asSequence()
            .map(secretSource::lookup)
            .firstOrNull { it.configured }
            ?.value
            ?.takeIf(String::isNotBlank)
            ?: error("Sentry token missing; set SENTRY_AUTH_TOKEN or SENTRY_TOKEN")
        val wire = transport.send(SentryApiRequest("GET", uri.toString(), token))
        val safeBody = redactionFilter.redact(wire.body)
        return SentryApiResponse(
            status = wire.status,
            body = safeBody,
            evidenceHash = redactionFilter.stableFingerprint("GET $normalizedPath\n$safeBody")
        )
    }

    private fun validatePath(raw: String): String {
        val path = raw.trim()
        require(path.startsWith("/api/0/") && !path.contains("..") && !path.contains('\\')) {
            "Sentry API path must remain inside /api/0/"
        }
        require(path.length <= 512) { "Sentry API path is too long" }
        return path
    }

    private fun segment(raw: String): String {
        val value = raw.trim()
        require(value.isNotBlank() && value.length <= 160 && value.all { it.isLetterOrDigit() || it in setOf('-', '_', '.') }) {
            "Sentry issue id is invalid"
        }
        return value
    }

    private fun validTerritory(raw: String): Boolean = raw.isNotBlank() &&
        !raw.startsWith('/') && !raw.contains('\\') && !raw.contains("..")

    private companion object {
        fun sendOverHttps(request: SentryApiRequest): SentryApiWireResponse {
            val httpRequest = HttpRequest.newBuilder(URI.create(request.url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer ${request.token}")
                .GET()
                .build()
            val response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString())
            return SentryApiWireResponse(response.statusCode(), response.body())
        }
    }
}

object SentryIssueParser {
    private val stringField = Regex("\\\"([A-Za-z0-9_]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
    private val frameObject = Regex("\\{[^{}]{0,800}\\\"filename\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"[^{}]{0,800}?(?:\\\"lineno\\\"\\s*:\\s*(\\d+))?[^{}]{0,200}\\}")

    fun parse(issueId: String, json: String): SentryIssue {
        val fields = stringField.findAll(json).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        val frames = frameObject.findAll(json).map { match ->
            SentryStackFrame(
                filename = unescape(match.groupValues[1]),
                lineNumber = match.groupValues.getOrNull(2)?.toIntOrNull()
            )
        }.toList()
        return SentryIssue(
            id = fields["id"]?.ifBlank { issueId } ?: issueId,
            title = fields["title"] ?: fields["message"] ?: "Sentry issue $issueId",
            culprit = fields["culprit"].orEmpty(),
            frames = frames,
            raw = json
        )
    }

    private fun unescape(value: String): String = value
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
}
