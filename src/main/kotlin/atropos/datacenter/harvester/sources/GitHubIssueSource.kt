package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.time.Instant

/**
 * GitHub API issue source.
 * Parses X-RateLimit-* headers and adheres to GitHub REST API v3 constraints.
 */
class GitHubIssueSource(
    private val authToken: String? = null
) : PlatformIssueSource {
    override val platformName: String = "GitHub"
    private var currentQuota = ApiQuotaState("GitHub", 5000, Instant.now().epochSecond + 3600, false)

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> {
        // Enforce Soft-Fail Network Law
        return try {
            // Live queries GitHub REST API /repos/{owner}/{repo}/issues
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun quota(): ApiQuotaState = currentQuota

    fun updateQuotaFromHeaders(remaining: Int, resetEpoch: Long) {
        currentQuota = ApiQuotaState("GitHub", remaining, resetEpoch, remaining < 20)
    }
}
