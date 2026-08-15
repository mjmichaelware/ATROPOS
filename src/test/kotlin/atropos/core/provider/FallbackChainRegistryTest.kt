/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FallbackChainRegistryTest {

    @Test
    fun `verifies providers and sorting by quota weight`() {
        val sorted = FallbackChainRegistry.getSortedProviders()
        assertEquals(0, sorted.first().quotaWeight)
        assertEquals("LocalToolchain", sorted.first().name)
        assertTrue(sorted[1].quotaWeight <= sorted[2].quotaWeight)
    }

    @Test
    fun `verifies OpenRouter free rotation when rate limited`() {
        assertEquals(1, FallbackChainRegistry.getActiveOpenRouterModel(rateLimited = false))
        assertEquals(3, FallbackChainRegistry.getActiveOpenRouterModel(rateLimited = true))
    }

    @Test
    fun `verifies task routing matrix integrity`() {
        assertEquals(14, FallbackChainRegistry.taskRoutingMatrix.size)
        val chatRoute = FallbackChainRegistry.taskRoutingMatrix.first { it.task == "chat" }
        assertEquals(1, chatRoute.preferredProviderId)
        assertEquals(2, chatRoute.fallbackProviderId)
        assertEquals(3, chatRoute.alternativeProviderId)
    }

    @Test
    fun `verifies named chains existence`() {
        assertEquals(listOf(0, 1, 29), FallbackChainRegistry.MEMORY_CHAIN)
        assertEquals(listOf(0, 29, 1), FallbackChainRegistry.SECRET_CHAIN)
    }
}
