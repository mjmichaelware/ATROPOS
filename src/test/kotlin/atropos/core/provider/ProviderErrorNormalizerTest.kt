/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderErrorNormalizerTest {

    private val normalizer = ProviderErrorNormalizer()

    @Test
    fun normalizes_empty_raw_message_to_empty_response() {
        val result = normalizer.normalize("openai", "")
        assertEquals(NormalizedProviderFailureType.EMPTY_RESPONSE, result.type)
        assertEquals("openai empty response", result.cleanSummary)
    }

    @Test
    fun normalizes_unauthorized_to_auth_failed() {
        val result = normalizer.normalize("openai", "HTTP 401: Unauthorized access token")
        assertEquals(NormalizedProviderFailureType.AUTH_FAILED, result.type)
        assertTrue(result.terminal)
    }

    @Test
    fun normalizes_rate_limit_to_rate_limited() {
        val result = normalizer.normalize("gemini", "Rate limit exceeded (429): please slow down")
        assertEquals(NormalizedProviderFailureType.RATE_LIMITED, result.type)
        assertEquals(300_000L, result.retryAfterMs)
    }

    @Test
    fun normalizes_insufficient_quota_to_billing_required() {
        val result = normalizer.normalize("openai", "insufficient_quota: billing required")
        assertEquals(NormalizedProviderFailureType.BILLING_REQUIRED, result.type)
        assertTrue(result.terminal)
    }

    @Test
    fun normalizes_socket_timeout_exception_to_timeout() {
        val ex = java.net.SocketTimeoutException("connect timed out")
        val result = normalizer.normalize("cloudflare", ex)
        assertEquals(NormalizedProviderFailureType.TIMEOUT, result.type)
        assertEquals(60_000L, result.retryAfterMs)
    }

    @Test
    fun normalizes_connection_refused_to_unavailable() {
        val result = normalizer.normalize("groq", "connection refused from endpoint")
        assertEquals(NormalizedProviderFailureType.UNAVAILABLE, result.type)
        assertEquals(120_000L, result.retryAfterMs)
    }

    @Test
    fun redacts_urls_and_secrets_in_failure_summaries() {
        val raw = "Error accessing https://api.openai.com/v1/chat/completions with key sk-proj-123456"
        val result = normalizer.normalize("openai", raw)
        assertNotNull(result.cleanSummary)
        assertFalse(result.cleanSummary.contains("sk-proj-"))
        assertFalse(result.cleanSummary.contains("https://"))
        assertTrue(result.cleanSummary.contains("<redacted:url>"))
    }
}
