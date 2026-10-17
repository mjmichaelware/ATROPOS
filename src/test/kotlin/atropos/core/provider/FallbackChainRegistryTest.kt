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

    @Test
    fun `parses raw network exceptions into network timeout category`() {
        val timeoutEx = java.net.SocketTimeoutException("Read timed out")
        val connectEx = java.net.ConnectException("Connection refused")

        val parsedTimeout = FallbackChainRegistry.parseError(timeoutEx)
        assertEquals(UniversalErrorCategory.NETWORK_TIMEOUT_DROP, parsedTimeout.category)

        val parsedConnect = FallbackChainRegistry.parseError(connectEx)
        assertEquals(UniversalErrorCategory.NETWORK_TIMEOUT_DROP, parsedConnect.category)
    }

    @Test
    fun `parses JSON HTTP error response bodies accurately`() {
        val contextJson = """{"error":{"message":"context length exceeded: max 8192 tokens","type":"invalid_request_error"}}"""
        val parsedContext = FallbackChainRegistry.parseError(contextJson, httpCode = 400)
        assertEquals(UniversalErrorCategory.CONTEXT_EXCEEDED, parsedContext.category)
        assertEquals("context length exceeded: max 8192 tokens", parsedContext.message)
        assertEquals("invalid_request_error", parsedContext.errorType)

        val rateLimitJson = """{"error":{"message":"Rate limit exceeded","type":"rate_limit_exceeded"}}"""
        val parsedRateLimit = FallbackChainRegistry.parseError(rateLimitJson, httpCode = 429)
        assertEquals(UniversalErrorCategory.RATE_LIMIT_OVERLOAD, parsedRateLimit.category)

        val authJson = """{"error":{"message":"Invalid API key provided","code":"invalid_api_key"}}"""
        val parsedAuth = FallbackChainRegistry.parseError(authJson, httpCode = 401)
        assertEquals(UniversalErrorCategory.AUTH_DEAD_KEY, parsedAuth.category)
    }

    @Test
    fun `routes context exceeded directly to high context frontier model`() {
        val err = FallbackChainRegistry.parseError("context window exceeded", httpCode = 413)
        val decision = FallbackChainRegistry.routeNext("groq", err, listOf("groq", "cerebras", "gemini"))
        assertEquals(RoutingAction.ROUTE_HIGH_CONTEXT, decision.action)
        assertTrue(decision.targetProvider in FallbackChainRegistry.HIGH_CONTEXT_FRONTIER_MODELS)
    }

    @Test
    fun `manages key arrays, dead key marking, and rotation`() {
        FallbackChainRegistry.resetState()
        val json = """{"groq_api_keys":["gkey-1","gkey-2","gkey-3"]}"""
        FallbackChainRegistry.loadKeysFromJson(json)

        assertEquals("gkey-1", FallbackChainRegistry.getActiveKey("groq"))

        // Simulate 401 Auth Dead Key: should mark dead and rotate to gkey-2
        val authErr = FallbackChainRegistry.parseError("Unauthorized", httpCode = 401)
        val decision1 = FallbackChainRegistry.routeNext("groq", authErr, listOf("groq", "gemini"))
        assertEquals(RoutingAction.ROTATE_KEY, decision1.action)
        assertEquals("gkey-2", decision1.targetKey)
        assertEquals("gkey-2", FallbackChainRegistry.getActiveKey("groq"))

        // Simulate 429 Rate Limit: should rotate to gkey-3
        val rateErr = FallbackChainRegistry.parseError("Rate limit", httpCode = 429)
        val decision2 = FallbackChainRegistry.routeNext("groq", rateErr, listOf("groq", "gemini"))
        assertEquals(RoutingAction.ROTATE_KEY, decision2.action)
        assertEquals("gkey-3", decision2.targetKey)
        assertEquals("gkey-3", FallbackChainRegistry.getActiveKey("groq"))

        // Simulate another 429 when no more keys exist: shifts to next parallel provider
        val decision3 = FallbackChainRegistry.routeNext("groq", rateErr, listOf("groq", "gemini"))
        assertEquals(RoutingAction.NEXT_PARALLEL_PROVIDER, decision3.action)
        assertEquals("gemini", decision3.targetProvider)

        FallbackChainRegistry.resetState()
    }

    @Test
    fun `active provider publishes real-time updates to listeners`() {
        val updates = mutableListOf<String>()
        val listener = { p: String -> updates.add(p); Unit }
        ActiveProvider.addListener(listener)

        ActiveProvider.set("groq")
        ActiveProvider.set("openrouter")
        ActiveProvider.set("gemini-2.5-pro")

        assertTrue(updates.contains("groq"))
        assertTrue(updates.contains("openrouter"))
        assertTrue(updates.contains("gemini-2.5-pro"))
        assertEquals("gemini-2.5-pro", ActiveProvider.get())

        ActiveProvider.removeListener(listener)
    }
}
