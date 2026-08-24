/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.projection.QuotaProjection
import atropos.core.provider.CostMode
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ProviderUsage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotaProjectionTest {
    @Test
    fun status_exposes_accounting_without_secret_values() {
        val registry = StaticProviderDescriptorRegistry()
        val ledger = InMemoryQuotaLedger(
            listOf(
                ProviderQuotaRecord(
                    providerId = "groq",
                    costMode = CostMode.FREE,
                    quotaWeight = 1,
                    configured = true,
                    verified = true,
                    state = ProviderAvailabilityState.READY,
                    usedRequests = 2,
                    usedTokens = 123
                )
            )
        )
        val json = QuotaProjection(registry, ledger).render()
        assertTrue(json.contains("\"id\":\"groq\""))
        assertTrue(json.contains("\"usedTokens\":123"))
        assertTrue(!json.contains("api_key", ignoreCase = true))
    }

    @Test
    fun remaining_quota_survives_ledger_restart_and_is_projected() {
        val root = Files.createTempDirectory("quota-remaining")
        val registry = StaticProviderDescriptorRegistry()
        val seed = InMemoryQuotaLedger.seedFromDescriptors(registry)
        val file = root.resolve("quota.tsv").toFile()
        val ledger = atropos.core.provider.FileQuotaLedger(file, seed)
        ledger.put(
            seed.first { it.providerId == "groq" }.copy(configured = true, verified = true)
        )
        ledger.recordSuccess(
            "groq",
            ProviderUsage(inputTokens = 2, outputTokens = 3, latencyMs = 1, remainingRequests = 17, remainingTokens = 900)
        )

        val reopened = atropos.core.provider.FileQuotaLedger(file, seed)
        val json = QuotaProjection(registry, reopened).render()
        assertTrue(json.contains("\"remainingRequests\":17"), json)
        assertTrue(json.contains("\"remainingTokens\":900"), json)
        assertEquals(1L, reopened.get("groq")?.latencyMsAvg)
    }
}
