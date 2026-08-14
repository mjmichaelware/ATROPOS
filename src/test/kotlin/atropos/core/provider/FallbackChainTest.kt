/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Source Doc 2 §.300 §5 and §6 are tables. Expressed as control flow they
 * cannot be printed, diffed, or checked against the document — so these check
 * the data against the document's own claims, which is the only thing a table
 * as data buys you over a table as branches.
 */
class FallbackChainTest {

    @Test
    fun `all eleven named chains exist`() {
        assertEquals(11, FallbackChain.entries.size)
        listOf(
            "CHAT_CHAIN", "CODE_CHAIN", "REPAIR_CHAIN", "PLANNING_CHAIN", "DOCS_CHAIN",
            "SEARCH_CHAIN", "EMBED_CHAIN", "MEMORY_CHAIN", "SECRET_CHAIN", "EDGE_CHAIN", "ASSET_CHAIN"
        ).forEach { name ->
            assertNotNull(FallbackChain.of(name), "$name is named in §6 and must exist")
        }
    }

    @Test
    fun `the chat chain matches the document literally`() {
        assertEquals(
            listOf("groq", "gemini", "openrouter_free", "github_models", "cloudflare_ai", "ollama", "paid_emergency"),
            FallbackChain.CHAT.links
        )
    }

    /**
     * The claim that motivated registering `local_toolchain` as provider 0: it
     * roots most of the chains, and a chain whose root is missing silently
     * begins at its second entry.
     */
    @Test
    fun `eight of the eleven chains begin on the local toolchain`() {
        assertEquals(8, FallbackChain.entries.count { it.localFirst })
    }

    @Test
    fun `a chain whose root is unregistered is reported broken`() {
        val withoutLocal = setOf("groq", "gemini")

        val broken = FallbackChain.brokenRoots(withoutLocal)

        assertTrue(broken.contains(FallbackChain.REPAIR))
        assertFalse(broken.contains(FallbackChain.CHAT), "chat roots at groq, which is registered")
    }

    @Test
    fun `terminal positions are not providers`() {
        assertFalse(ChainLink.isProvider(ChainLink.PAID_EMERGENCY))
        assertFalse(ChainLink.isProvider(ChainLink.QUEUED))
        assertTrue(ChainLink.isProvider("groq"))
        assertTrue(ChainLink.isProvider(ChainLink.LOCAL_TOOLCHAIN))
    }

    @Test
    fun `provider links exclude the terminal positions`() {
        assertFalse(FallbackChain.CHAT.providerLinks().contains(ChainLink.PAID_EMERGENCY))
        assertTrue(FallbackChain.CHAT.providerLinks().contains("ollama"))
    }

    @Test
    fun `paid emergency requires an unlock and is never reached by iterating`() {
        assertTrue(ChainLink.requiresUnlock(ChainLink.PAID_EMERGENCY))
        FallbackChain.entries.forEach { chain ->
            assertFalse(
                chain.providerLinks().contains(ChainLink.PAID_EMERGENCY),
                "${chain.canonical} must not expose paid as a walkable provider"
            )
        }
    }

    @Test
    fun `a chain renders as the document writes it`() {
        assertTrue(FallbackChain.CHAT.render().startsWith("CHAT_CHAIN=groq -> gemini"))
    }

    @Test
    fun `position drives preference and an absent provider is minus one`() {
        assertEquals(0, FallbackChain.CHAT.positionOf("groq"))
        assertEquals(1, FallbackChain.CHAT.positionOf("gemini"))
        assertEquals(-1, FallbackChain.CHAT.positionOf("nvidia"))
    }

    @Test
    fun `every chain a provider appears in can be listed`() {
        val chains = FallbackChain.containing("gemini").map { it.canonical }

        assertTrue(chains.contains("CHAT_CHAIN"))
        assertTrue(chains.contains("PLANNING_CHAIN"))
        assertTrue(chains.contains("DOCS_CHAIN"))
    }

    // -- routing matrix -------------------------------------------------------

    @Test
    fun `all fourteen routing rows exist`() {
        assertEquals(14, RoutedTask.entries.size)
        assertNotNull(RoutedTask.of("compile_repair"))
        assertNotNull(RoutedTask.of("secret_storage"))
    }

    @Test
    fun `every row has a populated degraded column`() {
        RoutedTask.entries.forEach { task ->
            assertTrue(task.degraded.isNotBlank(), "${task.canonical} has no degraded position")
        }
    }

    /**
     * The mechanical form of "forbid accidental paid calls": a caller looping a
     * task's free route to the end cannot land on a paid provider.
     */
    @Test
    fun `the free route never contains a paid provider`() {
        RoutedTask.entries.forEach { task ->
            task.paidEmergency?.let { paid ->
                assertFalse(
                    task.freeRoute().contains(paid),
                    "${task.canonical} exposes its paid provider on the free route"
                )
            }
        }
    }

    @Test
    fun `eleven of fourteen rows begin locally`() {
        assertEquals(11, RoutedTask.localFirstTasks().size)
    }

    @Test
    fun `a row naming an unregistered provider is reported rather than silently skipped`() {
        val unresolved = RoutedTask.unresolvedProviders(setOf("groq", "gemini"))

        assertTrue(unresolved.containsKey(RoutedTask.CHAT_PROMPT))
        assertTrue(unresolved.getValue(RoutedTask.CHAT_PROMPT).contains("openrouter_free"))
    }

    @Test
    fun `compile repair begins locally, per the document`() {
        assertEquals(ChainLink.LOCAL_TOOLCHAIN, RoutedTask.COMPILE_REPAIR.first)
        assertEquals("groq", RoutedTask.COMPILE_REPAIR.second)
    }
}
