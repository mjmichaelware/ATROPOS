package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FactoryResearchDloiTest {
    @Test
    fun `factory research records the canonical DLOI outcome and preserves no cosine fallback`() {
        val root = Files.createTempDirectory("atropos-factory-dloi-")
        val report = FactoryResearchService().collect(
            root = root,
            prompt = "build a bounded authority CLI",
            projectId = "factory-dloi",
            promptFingerprint = "prompt-${"a".repeat(16)}",
            promptSpans = "CLI@20-22"
        )

        assertTrue(report.channelLog.any { it.startsWith("dloi=") })
        assertTrue(report.channelLog.any { it.startsWith("dloi_route=") })
        assertTrue(report.channelLog.any { it.contains("no_cosine_rag") })
    }

    @Test
    fun `zero retention skips remote research channels and records the policy outcome`() {
        val root = Files.createTempDirectory("atropos-factory-zero-retention-")
        val report = FactoryResearchService(zeroRetentionResearch = true).collect(
            root = root,
            prompt = "research a remote authority",
            projectId = "factory-zero-retention"
        )

        assertTrue(report.channelLog.contains("lakehouse=SKIPPED_ZERO_RETENTION"))
        assertTrue(report.channelLog.contains("lakehouse_route=SKIPPED_ZERO_RETENTION:remote_research_disabled"))
        assertTrue(report.channelLog.contains("bounded_fetch=SKIPPED_ZERO_RETENTION"))
        assertTrue(report.fetchHashes.isEmpty())
    }
}
