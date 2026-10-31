/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.harvester

import atropos.datacenter.harvester.sources.GitHubIssueSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitHubIssueSourceTest {

    @Test
    fun `RepositoryConsentGate authorizes explicit repo mjmichaelware ATROPOS`() {
        val gate = RepositoryConsentGate()
        val consent = gate.checkConsent("GitHub", "mjmichaelware/ATROPOS")
        assertTrue(consent.isAuthorized)
        assertTrue(consent.reason.contains("allowlist"))
    }

    @Test
    fun `GitHubIssueSource adheres to Soft-Fail Network Law on invalid target`() {
        val source = GitHubIssueSource()
        val issues = source.fetchOpenIssues("nonexistent/repo-invalid-12345")
        assertNotNull(issues)
        assertTrue(issues.isEmpty(), "Must return emptyList under Soft-Fail Network Law")
    }

    @Test
    fun `GitHubIssueSource updates quota correctly`() {
        val source = GitHubIssueSource()
        source.updateQuotaFromHeaders(remaining = 15, resetEpoch = 1234567890L)
        val quota = source.quota()
        assertEquals(15, quota.remainingCalls)
        assertTrue(quota.isThrottled, "Should be throttled when remaining < 20")
    }
}
