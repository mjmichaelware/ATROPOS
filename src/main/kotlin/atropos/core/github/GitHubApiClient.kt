/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.PolicyActionClass
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

data class GitHubApiRequest(
    val method: String,
    val path: String,
    val body: String? = null,
    val declaredTerritory: List<String> = listOf(".")
)

data class GitHubApiResponse(
    val status: Int,
    val body: String,
    val evidenceHash: String
)

fun interface GitHubApiTransport {
    fun send(request: GitHubApiWireRequest): GitHubApiWireResponse
}

data class GitHubApiWireRequest(
    val method: String,
    val url: String,
    val body: String?,
    val token: String
)

data class GitHubApiWireResponse(val status: Int, val body: String)

/**
 * Typed REST boundary for the existing GitHub integration.
 *
 * It deliberately does not model every GitHub object. The generic request
 * shape supports the official GitHub MCP/API surface while keeping one gate,
 * one token source, one redaction path, and one injectable transport.
 */
class GitHubApiClient(
    private val secretSource: SecretSource = DefaultSecretSource.create(),
    private val gate: (ActionProposal) -> AgencyDecision = BoundedAgencyGate()::evaluate,
    private val transport: GitHubApiTransport = ::sendOverHttps,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun execute(request: GitHubApiRequest): GitHubApiResponse {
        val method = request.method.trim().uppercase()
        require(method in METHODS) { "unsupported GitHub API method: $method" }
        val path = validatePath(request.path)
        require(request.body.orEmpty().length <= MAX_BODY_CHARS) {
            "GitHub API request body exceeds $MAX_BODY_CHARS characters"
        }
        val territory = request.declaredTerritory.map(::validateTerritoryPath)
        require(territory.isNotEmpty()) { "GitHub API request requires declared territory" }
        if (!SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
            error("GitHub API refused: SecretSinkMatrix does not permit network credential egress")
        }

        val proposal = ActionProposal(
            id = "github-api-${redactionFilter.stableFingerprint("$method $path")}",
            actionClass = PolicyActionClass.NETWORK,
            actor = ActionActor.HumanOwner,
            targetPaths = territory,
            networkTarget = "api.github.com",
            metadata = mapOf("integration" to "github", "operation" to method.lowercase())
        )
        val decision = gate(proposal)
        when (decision.disposition) {
            AgencyDisposition.ALLOWED -> Unit
            AgencyDisposition.APPROVAL_REQUIRED -> error(
                "GitHub API requires explicit approval: ${decision.reason}"
            )
            AgencyDisposition.POLICY_BLOCKED -> error("GitHub API refused by policy: ${decision.reason}")
        }

        val token = TOKEN_NAMES.asSequence()
            .map(secretSource::lookup)
            .firstOrNull { it.configured }
            ?.value
            ?.takeIf { it.isNotBlank() }
            ?: error("GitHub API token missing; set ATROPOS_GITHUB_TOKEN or GITHUB_TOKEN")
        val response = transport.send(
            GitHubApiWireRequest(method, "$API_ROOT$path", request.body, token)
        )
        val safeBody = redactionFilter.redact(response.body)
        return GitHubApiResponse(
            status = response.status,
            body = safeBody,
            evidenceHash = redactionFilter.stableFingerprint("$method $path\n$safeBody")
        )
    }

    fun listIssues(owner: String, repository: String, page: Int = 1): GitHubApiResponse =
        execute(GitHubApiRequest("GET", repoPath(owner, repository, "issues?page=${page.coerceAtLeast(1)}")))

    fun getIssue(owner: String, repository: String, number: Int): GitHubApiResponse =
        execute(GitHubApiRequest("GET", repoPath(owner, repository, "issues/${positive(number)}")))

    fun createIssue(owner: String, repository: String, body: String): GitHubApiResponse =
        execute(GitHubApiRequest("POST", repoPath(owner, repository, "issues"), body))

    fun commentIssue(owner: String, repository: String, number: Int, body: String): GitHubApiResponse =
        execute(GitHubApiRequest("POST", repoPath(owner, repository, "issues/${positive(number)}/comments"), body))

    fun listPullRequests(owner: String, repository: String, page: Int = 1): GitHubApiResponse =
        execute(GitHubApiRequest("GET", repoPath(owner, repository, "pulls?page=${page.coerceAtLeast(1)}")))

    fun getPullRequestFiles(owner: String, repository: String, number: Int): GitHubApiResponse =
        execute(GitHubApiRequest("GET", repoPath(owner, repository, "pulls/${positive(number)}/files")))

    fun listCheckRuns(owner: String, repository: String, ref: String): GitHubApiResponse =
        execute(GitHubApiRequest("GET", repoPath(owner, repository, "commits/${refPath(ref)}/check-runs")))

    fun createCheckRun(owner: String, repository: String, body: String): GitHubApiResponse =
        execute(GitHubApiRequest("POST", repoPath(owner, repository, "check-runs"), body))

    fun updateCheckRun(owner: String, repository: String, runId: Long, body: String): GitHubApiResponse =
        execute(GitHubApiRequest("PATCH", repoPath(owner, repository, "check-runs/${positive(runId)}"), body))

    private fun repoPath(owner: String, repository: String, suffix: String): String {
        val safeOwner = segment(owner)
        val safeRepository = segment(repository.removeSuffix(".git"))
        return "/repos/$safeOwner/$safeRepository/$suffix"
    }

    private fun refPath(raw: String): String = raw.trim().split('/').joinToString("/") { segment(it) }

    private fun positive(value: Int): Int {
        require(value > 0) { "GitHub API numeric identifier must be positive" }
        return value
    }

    private fun positive(value: Long): Long {
        require(value > 0) { "GitHub API numeric identifier must be positive" }
        return value
    }

    private fun validatePath(raw: String): String {
        val path = raw.trim()
        require(path.startsWith("/repos/") && !path.contains("..") && !path.contains('\\')) {
            "GitHub API path must be a repository-scoped relative API path"
        }
        require(path.length <= MAX_PATH_CHARS) { "GitHub API path is too long" }
        return path
    }

    private fun validateTerritoryPath(raw: String): String {
        val path = raw.trim()
        require(path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains("..")) {
            "GitHub API territory must be relative and traversal-free"
        }
        return path
    }

    private fun segment(raw: String): String {
        val value = raw.trim()
        require(value.isNotBlank() && value.length <= 120 && !value.contains('/') && !value.contains("..")) {
            "GitHub API path segment is invalid"
        }
        require(value.all { it.isLetterOrDigit() || it in setOf('.', '-', '_') }) {
            "GitHub API path segment contains unsupported characters"
        }
        return value
    }

    private companion object {
        const val API_ROOT = "https://api.github.com"
        const val MAX_BODY_CHARS = 64 * 1024
        const val MAX_PATH_CHARS = 512
        val METHODS = setOf("GET", "POST", "PATCH")
        val TOKEN_NAMES = listOf("ATROPOS_GITHUB_TOKEN", "GITHUB_TOKEN", "GH_TOKEN")

        fun sendOverHttps(request: GitHubApiWireRequest): GitHubApiWireResponse {
            val builder = HttpRequest.newBuilder(URI.create(request.url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer ${request.token}")
                .header("X-GitHub-Api-Version", "2022-11-28")
            val body = request.body?.let(HttpRequest.BodyPublishers::ofString)
                ?: HttpRequest.BodyPublishers.noBody()
            val httpRequest = when (request.method) {
                "GET" -> builder.GET().build()
                "POST" -> builder.POST(body).header("Content-Type", "application/json").build()
                "PATCH" -> builder.method("PATCH", body).header("Content-Type", "application/json").build()
                else -> error("unsupported GitHub API method: ${request.method}")
            }
            val response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString())
            return GitHubApiWireResponse(response.statusCode(), response.body())
        }
    }
}
