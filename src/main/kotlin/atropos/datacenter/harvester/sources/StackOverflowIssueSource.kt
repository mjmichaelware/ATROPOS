package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord
import java.time.Instant

/**
 * StackExchange API source for bug repro snippets and accepted answers.
 */
class StackOverflowIssueSource : PlatformIssueSource {
    override val platformName: String = "StackOverflow"
    private var currentQuota = ApiQuotaState("StackOverflow", 300, Instant.now().epochSecond + 86400, false)

    override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> = emptyList()
    override fun quota(): ApiQuotaState = currentQuota
}
