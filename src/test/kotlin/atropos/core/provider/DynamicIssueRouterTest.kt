/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DynamicIssueRouterTest {

    @Test
    fun `resolves brackets based on token count`() {
        val router = DynamicIssueRouter()
        assertEquals(ContextBracket.RANGE_8K, router.resolveBracket(5_000))
        assertEquals(ContextBracket.RANGE_8K, router.resolveBracket(8_000))
        assertEquals(ContextBracket.RANGE_32K, router.resolveBracket(8_001))
        assertEquals(ContextBracket.RANGE_32K, router.resolveBracket(32_000))
        assertEquals(ContextBracket.RANGE_128K, router.resolveBracket(32_001))
        assertEquals(ContextBracket.RANGE_128K, router.resolveBracket(128_000))
        assertEquals(ContextBracket.RANGE_OVER_128K, router.resolveBracket(128_001))
        assertEquals(ContextBracket.RANGE_OVER_128K, router.resolveBracket(500_000))
    }

    @Test
    fun `selects appropriate provider and evicts sticky state`() {
        val router = DynamicIssueRouter()
        val providers = listOf(
            ProviderDescriptor("groq", "Groq Llama 3.3", CostMode.FREE, 1, emptySet(), contextWindowTokens = 8_192),
            ProviderDescriptor("deepseek_direct", "DeepSeek V3", CostMode.FREE, 1, emptySet(), contextWindowTokens = 64_000),
            ProviderDescriptor("gemini", "Gemini 2.0 Flash", CostMode.FREE, 1, emptySet(), contextWindowTokens = 1_000_000)
        )

        val targetSmall = router.selectNextProvider("issue-1", 4_000, providers)
        assertEquals("groq", targetSmall.selectedProviderId)
        assertEquals(ContextBracket.RANGE_8K, targetSmall.bracket)

        val targetMedium = router.selectNextProvider("issue-2", 20_000, providers)
        assertEquals("deepseek_direct", targetMedium.selectedProviderId)
        assertEquals(ContextBracket.RANGE_32K, targetMedium.bracket)

        val targetHuge = router.selectNextProvider("issue-3", 250_000, providers)
        assertEquals("gemini", targetHuge.selectedProviderId)
        assertEquals(ContextBracket.RANGE_OVER_128K, targetHuge.bracket)

        router.evictStickyState()
    }
}
