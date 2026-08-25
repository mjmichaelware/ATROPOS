package atropos.core.github

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeCommandResult
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/** A non-secret operator capability required before a GitHub side effect. */
data class GitHubPushAuthorization(
    val operatorId: String,
    val confirmationId: String
) {
    init {
        require(operatorId.isNotBlank()) { "GitHub push operator is required" }
        require(confirmationId.isNotBlank()) { "GitHub push confirmation is required" }
    }
}

data class GitHubRepositoryRequest(
    val repositoryName: String,
    val defaultBranch: String
)

data class GitHubPushRequest(
    val repositoryRoot: Path,
    val branch: String,
    val changedPaths: List<String>,
    val declaredTerritory: List<String>,
    val authorization: GitHubPushAuthorization?
)

data class GitHubBindingResult(
    val allowed: Boolean,
    val operation: String,
    val message: String,
    val command: GitWorktreeCommandResult? = null
)

fun interface GitHubRepositoryProvisioner {
    fun create(request: GitHubRepositoryRequest): GitHubBindingResult
}

/**
 * Canonical boundary for GitHub repository side effects.
 *
 * This class owns authorization and territory decisions. It delegates local
 * Git execution to [BoundedGitWorktreeCommandRunner] and delegates remote
 * repository creation to a credential-aware provisioner. It never accepts a
 * credential value and never infers push consent from a successful build.
 */
class GitHubBinding(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val repositoryProvisioner: GitHubRepositoryProvisioner = GitHubRepositoryProvisioner {
        GitHubBindingResult(false, "create", "GitHub repository provisioner is not configured")
    },
    private val apiClient: GitHubApiClient = GitHubApiClient()
) {
    /** The sole production handoff into the gated GitHub REST owner. */
    fun api(request: GitHubApiRequest): GitHubApiResponse = apiClient.execute(request)

    fun listIssues(owner: String, repository: String, page: Int = 1): GitHubApiResponse =
        apiClient.listIssues(owner, repository, page)

    fun getIssue(owner: String, repository: String, number: Int): GitHubApiResponse =
        apiClient.getIssue(owner, repository, number)

    fun createIssue(owner: String, repository: String, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.createIssue(owner, repository, body, authorization)

    fun commentIssue(owner: String, repository: String, number: Int, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.commentIssue(owner, repository, number, body, authorization)

    fun listPullRequests(owner: String, repository: String, page: Int = 1): GitHubApiResponse =
        apiClient.listPullRequests(owner, repository, page)

    fun getPullRequestFiles(owner: String, repository: String, number: Int): GitHubApiResponse =
        apiClient.getPullRequestFiles(owner, repository, number)

    fun createPullRequest(owner: String, repository: String, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.createPullRequest(owner, repository, body, authorization)

    fun commentPullRequest(owner: String, repository: String, number: Int, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.commentPullRequest(owner, repository, number, body, authorization)

    fun requestPullReview(owner: String, repository: String, number: Int, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.requestPullReview(owner, repository, number, body, authorization)

    fun listCheckRuns(owner: String, repository: String, ref: String): GitHubApiResponse =
        apiClient.listCheckRuns(owner, repository, ref)

    fun createCheckRun(owner: String, repository: String, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.createCheckRun(owner, repository, body, authorization)

    fun updateCheckRun(owner: String, repository: String, runId: Long, body: String, authorization: GitHubWriteAuthorization): GitHubApiResponse =
        apiClient.updateCheckRun(owner, repository, runId, body, authorization)

    fun getBranchProtection(owner: String, repository: String, branch: String): GitHubApiResponse =
        apiClient.getBranchProtection(owner, repository, branch)

    fun fileBlame(owner: String, repository: String, revision: String, path: String): GitHubApiResponse =
        apiClient.fileBlame(owner, repository, revision, path)

    fun createRepository(request: GitHubRepositoryRequest): GitHubBindingResult {
        validateRepositoryName(request.repositoryName)
        validateBranch(request.defaultBranch)
        return repositoryProvisioner.create(request)
    }

    fun push(request: GitHubPushRequest): GitHubBindingResult {
        val refusal = validatePush(request)
        if (refusal != null) return refusal
        val command = gitRunner.run(GitWorktreeOperation.PUSH, request.repositoryRoot)
        return GitHubBindingResult(
            allowed = command.exitCode == 0,
            operation = "push",
            message = if (command.exitCode == 0) {
                "GitHub push completed for branch ${request.branch}"
            } else {
                "GitHub push failed with exit=${command.exitCode}"
            },
            command = command
        )
    }

    private fun validatePush(request: GitHubPushRequest): GitHubBindingResult? {
        validateBranch(request.branch)
        if (request.authorization == null) {
            return GitHubBindingResult(false, "push", "explicit GitHub push authorization is required")
        }
        if (request.declaredTerritory.isEmpty()) {
            return GitHubBindingResult(false, "push", "GitHub push requires declared territory")
        }
        val normalizedTerritory = request.declaredTerritory.map(::normalizeRelative)
        val outside = request.changedPaths.map(::normalizeRelative).filter { path ->
            normalizedTerritory.none { territory -> path == territory || path.startsWith("$territory/") }
        }
        if (outside.isNotEmpty()) {
            return GitHubBindingResult(
                false,
                "push",
                "GitHub push refused outside declared territory: ${outside.joinToString(",")}"
            )
        }
        return null
    }

    private fun validateRepositoryName(value: String) {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))) {
            "invalid GitHub repository name"
        }
    }

    private fun validateBranch(value: String) {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]*"))) { "invalid GitHub branch" }
        require(!value.contains("..") && !value.contains("@{") && !value.any(Char::isWhitespace)) {
            "invalid GitHub branch"
        }
        require(!value.startsWith("/") && !value.endsWith("/") && !value.startsWith(".") && !value.endsWith(".")) {
            "invalid GitHub branch"
        }
    }

    private fun normalizeRelative(value: String): String {
        val raw = value.trim()
        require(raw.isNotBlank() && !raw.startsWith("/") && !raw.startsWith("\\")) {
            "GitHub territory paths must be relative and traversal-free"
        }
        val normalized = raw.replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && !normalized.split('/').contains("..")) {
            "GitHub territory paths must be relative and traversal-free"
        }
        return normalized
    }
}
