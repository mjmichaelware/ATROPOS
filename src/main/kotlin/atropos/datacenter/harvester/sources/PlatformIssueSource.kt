package atropos.datacenter.harvester.sources

import atropos.datacenter.harvester.ApiQuotaState
import atropos.datacenter.harvester.IssueRecord

/**
 * Interface implemented by every decoupled scraping source.
 */
interface PlatformIssueSource {
    val platformName: String
    fun fetchOpenIssues(targetRepo: String): List<IssueRecord>
    fun quota(): ApiQuotaState
}
