package atropos.datacenter.harvester

import atropos.datacenter.harvester.sources.PlatformIssueSource
import java.time.Instant

/**
 * Unified API Polling Daemon & Rate Limit Governor.
 * Multiplexes across GitHub, GitLab, and Jira 24/7 without quota exhaustion.
 */
class UnifiedPollingDaemon(
    val sources: List<PlatformIssueSource>,
    val consentGate: RepositoryConsentGate
) {
    fun evaluateQuotaBackpressure(source: PlatformIssueSource): Long {
        val q = source.quota()
        if (q.isThrottled) {
            val waitSec = maxOf(q.resetAtEpochSeconds - Instant.now().epochSecond, 5L)
            return waitSec
        }
        return 0L
    }

    fun pollAuthorizedTargets(targetRepos: List<String>): List<IssueRecord> {
        val results = mutableListOf<IssueRecord>()
        for (source in sources) {
            val backpressure = evaluateQuotaBackpressure(source)
            if (backpressure > 0) continue

            for (target in targetRepos) {
                val consent = consentGate.checkConsent(source.platformName, target)
                if (!consent.isAuthorized) continue

                val issues = source.fetchOpenIssues(target)
                results.addAll(issues)
            }
        }
        return results
    }
}
