/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Priority #7 — local-first, free-first.
 *
 * The shipped order put `ollama` last and named no local provider in the patch
 * order at all, so every case below asserts a change in which provider the
 * cascade reaches first.
 */
class ProviderCascadeOrderTest {

    @Test
    fun the_local_provider_runs_first_even_when_listed_last() {
        // The exact shipped ask order, local last.
        val shipped = listOf("groq", "github_models", "cloudflare_ai", "sambanova", "ollama")

        val ordered = ProviderCascadeOrder.order(shipped)

        assertEquals("ollama", ordered.first(), "local must be tried before anything remote")
        assertEquals(shipped.size, ordered.size, "ordering must not drop configured providers")
    }

    @Test
    fun free_providers_run_before_metered_ones() {
        val ordered = ProviderCascadeOrder.order(listOf("github_models", "cloudflare_ai", "groq"))

        // groq is FREE; github_models and cloudflare_ai are COOLDOWN_OK.
        assertEquals("groq", ordered.first())
    }

    @Test
    fun a_paid_locked_provider_never_enters_the_cascade() {
        val ordered = ProviderCascadeOrder.order(listOf("openai", "groq", "deepseek_direct", "ollama"))

        assertTrue(ordered.none { it == "openai" }, "paid providers must not be attempted: $ordered")
        assertTrue(ordered.none { it == "deepseek_direct" }, "paid providers must not be attempted: $ordered")
        assertEquals(listOf("ollama", "groq"), ordered)
    }

    @Test
    fun an_unknown_provider_is_ranked_last_not_assumed_free() {
        val ordered = ProviderCascadeOrder.order(listOf("mystery_provider", "groq"))

        assertEquals(listOf("groq", "mystery_provider"), ordered, "unknown cost is not free cost")
    }

    @Test
    fun preference_between_peers_of_the_same_cost_is_preserved() {
        val a = ProviderCascadeOrder.order(listOf("github_models", "cloudflare_ai"))
        val b = ProviderCascadeOrder.order(listOf("cloudflare_ai", "github_models"))

        assertEquals(listOf("github_models", "cloudflare_ai"), a)
        assertEquals(listOf("cloudflare_ai", "github_models"), b)
    }

    @Test
    fun ordering_is_idempotent_and_deduplicates() {
        val once = ProviderCascadeOrder.order(listOf("groq", "ollama", "groq"))
        assertEquals(once, ProviderCascadeOrder.order(once))
        assertEquals(listOf("ollama", "groq"), once)
    }

    @Test
    fun local_classification_comes_from_the_registry() {
        assertTrue(ProviderCascadeOrder.isLocal("ollama"))
        assertTrue(!ProviderCascadeOrder.isLocal("groq"))
        assertTrue(!ProviderCascadeOrder.isLocal("mystery_provider"))
    }
}
