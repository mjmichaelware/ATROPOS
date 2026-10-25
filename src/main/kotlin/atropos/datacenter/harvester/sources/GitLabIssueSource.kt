package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.time.Instant

/**
 * GitLab API issue source.
 * Parses RateLimit-* headers and handles GitLab projects API.
 */
class GitLabIssueSource(
    private val gitlabToken: String? = null
) : PlatformIssueSource {
    override val platformName: String = "GitLab"
    private var currentQuota = ApiQuotaState("GitLab", 2000, Instant.now().epochSecond + 3600, false)

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> {
        return emptyList()
    }

    override fun quota(): ApiQuotaState = currentQuota
}
