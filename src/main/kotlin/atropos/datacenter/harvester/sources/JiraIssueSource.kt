package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.time.Instant

/**
 * Jira REST API issue source for enterprise bug trackers.
 */
class JiraIssueSource : PlatformIssueSource {
    override val platformName: String = "Jira"
    private var currentQuota = ApiQuotaState("Jira", 1000, Instant.now().epochSecond + 3600, false)

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> = emptyList()
    override fun quota(): ApiQuotaState = currentQuota
}
