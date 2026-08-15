// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.provider

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class TypedFailureStatesTest {
    @Test
    fun `parses standard HTTP status error codes`() {
        val resetTime = Instant.now().plusSeconds(60)
        val exhausted = ProviderFailureState.fromErrorCode(429, resetTime)
        assertTrue(exhausted is ProviderFailureState.ExhaustedUntilReset)
        assertEquals(resetTime, (exhausted as ProviderFailureState.ExhaustedUntilReset).resetAt)

        val billing = ProviderFailureState.fromErrorCode(402)
        assertTrue(billing is ProviderFailureState.BillingRequired)

        val auth = ProviderFailureState.fromErrorCode(403)
        assertTrue(auth is ProviderFailureState.AuthFailed)

        val missing = ProviderFailureState.fromErrorCode(404, alternateModel = "claude")
        assertTrue(missing is ProviderFailureState.ModelMissing)
        assertEquals("claude", (missing as ProviderFailureState.ModelMissing).alternateModel)
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        kotlin.test.assertEquals(expected, actual)
    }
}
