package atropos.datacenter.harvester

import atropos.datacenter.harvester.sources.PlatformIssueSource
import atropos.datacenter.shipper.HuggingFaceShipper
import atropos.datacenter.storage.NeurodevelopmentalTelemetryStreamer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class AutonomousIssueHarvesterServiceTest {

    private class FakeIssueSource(
        override val platformName: String = "GitHub",
        private val issues: List<IssueRecord> = emptyList(),
        private val quotaState: ApiQuotaState = ApiQuotaState("GitHub", 5000, Instant.now().epochSecond + 3600, false)
    ) : PlatformIssueSource {
        override fun fetchOpenIssues(targetRepo: String): List<IssueRecord> = issues
        override fun quota(): ApiQuotaState = quotaState
    }

    @Test
    fun `harvester service executes end-to-end pull and triages issues with competitor redaction`() {
        val testIssues = listOf(
            IssueRecord(
                platform = "GitHub",
                repository = "mjmichaelware/ATROPOS",
                issueId = "101",
                title = "Fix parser bug reported by Claude assistant",
                body = "Investigate Copilot tabnine completion anomaly",
                labels = listOf("bug", "triage")
            )
        )
        val source = FakeIssueSource(issues = testIssues)
        val service = AutonomousIssueHarvesterService(
            sources = listOf(source),
            consentGate = RepositoryConsentGate(),
            triagePipeline = AutonomousIssueTriagePipeline(),
            streamer = NeurodevelopmentalTelemetryStreamer(),
            shipper = HuggingFaceShipper(apiKey = "mock-key")
        )

        val summary = service.executeHarvesterRun(listOf("mjmichaelware/ATROPOS"))

        assertEquals(1, summary.totalItemsHarvested)
        assertEquals(1, summary.triagedItemsCount)
        assertFalse(summary.isThrottled)
        assertTrue(summary.items.isNotEmpty())

        val item = summary.items.first()
        assertEquals("101", item.id)
        assertTrue(item.triageSuccess)
        // Verify competitor names are completely redacted from summary title
        assertFalse(item.title.contains("Claude", ignoreCase = true))
        assertTrue(item.title.contains("[REDACTED_PROVIDER]"))
    }

    @Test
    fun `harvester honors repository consent gate and skips unauthorized repositories`() {
        val testIssues = listOf(
            IssueRecord(
                platform = "GitHub",
                repository = "unauthorized-org/private-repo",
                issueId = "999",
                title = "Should not be triaged",
                body = ""
            )
        )
        val source = FakeIssueSource(issues = testIssues)
        val service = AutonomousIssueHarvesterService(
            sources = listOf(source),
            consentGate = RepositoryConsentGate(),
            triagePipeline = AutonomousIssueTriagePipeline(),
            streamer = NeurodevelopmentalTelemetryStreamer(),
            shipper = HuggingFaceShipper(apiKey = "mock-key")
        )

        val summary = service.executeHarvesterRun(listOf("unauthorized-org/private-repo"))

        assertEquals(0, summary.totalItemsHarvested)
        assertEquals(0, summary.triagedItemsCount)
    }
}
