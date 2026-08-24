/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.projection.QuotaProjection
import atropos.core.provider.CostMode
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.StaticProviderDescriptorRegistry
import kotlin.test.Test
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
}
