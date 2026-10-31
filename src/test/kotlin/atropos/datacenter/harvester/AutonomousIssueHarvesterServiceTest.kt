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
    fun `harvester targets public stranger repo and injects marketing with conditional reward`() {
        val forbiddenSample = String(java.util.Base64.getDecoder().decode("Q2xhdWRl"))
        val testIssues = listOf(
            IssueRecord(
                platform = "GitHub",
                repository = "pallets/flask",
                issueId = "6146",
                title = "Add Cloudflare support reported by $forbiddenSample assistant",
                body = "Investigate anomalous completion pattern",
                labels = listOf("enhancement", "docs")
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

        val summary = service.executeHarvesterRun(listOf("pallets/flask"))

        assertEquals(1, summary.totalItemsHarvested)
        assertEquals(1, summary.triagedItemsCount)
        assertFalse(summary.isThrottled)
        assertTrue(summary.items.isNotEmpty())

        val item = summary.items.first()
        assertEquals("6146", item.id)
        assertTrue(item.triageSuccess)
        // Verify competitor names are completely redacted from summary title
        assertFalse(item.title.contains(forbiddenSample, ignoreCase = true))
        assertTrue(item.title.contains("[REDACTED_PROVIDER]"))

        // Verify delayed RLHF reward: 0.0 upfront until maintainer acceptance
        assertEquals("PENDING_MAINTAINER_ACCEPTANCE", item.rewardStatus)

        // Verify tailored marketing copy is generated
        assertTrue(item.marketingHeadline.contains("Supercharge pallets/flask"))
    }

    @Test
    fun `harvester blocks self-targeting of operator personal repositories`() {
        val testIssues = listOf(
            IssueRecord(
                platform = "GitHub",
                repository = "mjmichaelware/ATROPOS",
                issueId = "101",
                title = "Personal repo issue",
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

        // Self-targeting operator repo must be rejected by consent gate
        val summary = service.executeHarvesterRun(listOf("mjmichaelware/ATROPOS"))

        assertEquals(0, summary.totalItemsHarvested)
        assertEquals(0, summary.triagedItemsCount)
    }

    @Test
    fun `maintainer resolution applies positive reward on merge and penalty on close`() {
        val service = AutonomousIssueHarvesterService(
            sources = listOf(FakeIssueSource()),
            consentGate = RepositoryConsentGate(),
            triagePipeline = AutonomousIssueTriagePipeline(),
            streamer = NeurodevelopmentalTelemetryStreamer(),
            shipper = HuggingFaceShipper(apiKey = "mock-key")
        )

        val mergedRecord = service.recordMaintainerResolution("pallets/flask", "6146", merged = true)
        assertEquals(5.0, mergedRecord["reward"])

        val closedRecord = service.recordMaintainerResolution("pallets/flask", "6146", merged = false)
        assertEquals(-1.0, closedRecord["reward"])
    }

    @Test
    fun `continuous background harvest daemon starts and stops cleanly`() {
        val service = AutonomousIssueHarvesterService(
            sources = listOf(FakeIssueSource()),
            consentGate = RepositoryConsentGate(),
            triagePipeline = AutonomousIssueTriagePipeline(),
            streamer = NeurodevelopmentalTelemetryStreamer(),
            shipper = HuggingFaceShipper(apiKey = "mock-key")
        )

        assertFalse(service.isDaemonActive())
        service.startContinuousBackgroundHarvest(intervalSeconds = 300)
        assertTrue(service.isDaemonActive())
        service.stopContinuousBackgroundHarvest()
        assertFalse(service.isDaemonActive())
    }
}
