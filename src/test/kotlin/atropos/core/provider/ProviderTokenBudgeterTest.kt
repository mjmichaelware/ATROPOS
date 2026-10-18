/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderTokenBudgeterTest {

    @BeforeTest
    fun setUp() {
        ProviderTokenBudgeter.reset()
    }

    @Test
    fun calculates_minimal_repair_token_budget_based_on_lines() {
        // min(lines * 4 + 512, 2048)
        val budget10Lines = ProviderTokenBudgeter.calculateRepairBudget(10)
        assertEquals(552, budget10Lines)

        val budget100Lines = ProviderTokenBudgeter.calculateRepairBudget(100)
        assertEquals(912, budget100Lines)

        val budget1000Lines = ProviderTokenBudgeter.calculateRepairBudget(1000)
        assertEquals(2048, budget1000Lines) // clamped to ceiling
    }

    @Test
    fun clamps_budget_below_known_credit_balance() {
        // OpenRouter reports balance affording 947 tokens
        val errorBody = "HTTP 402 Payment Required: account balance only affording 947 tokens"
        ProviderTokenBudgeter.recordFeedback("openrouter", 402, emptyMap(), errorBody)

        val cap = ProviderTokenBudgeter.getCap("openrouter")
        assertEquals(947, cap)

        // Subsequent budget requests are clamped below the balance threshold
        val budget = ProviderTokenBudgeter.budgetFor("openrouter", requestedTokens = 4096)
        assertTrue(budget <= 947 - 50)
        assertEquals(897, budget)
    }

    @Test
    fun parses_rate_limit_token_headers() {
        val headers = mapOf(
            "x-ratelimit-remaining-tokens" to listOf("1500")
        )
        ProviderTokenBudgeter.recordFeedback("groq", 200, headers, "{}")

        val cap = ProviderTokenBudgeter.getCap("groq")
        assertEquals(1500, cap)

        val budget = ProviderTokenBudgeter.budgetFor("groq", requestedTokens = 4096)
        assertEquals(1450, budget)
    }
}
