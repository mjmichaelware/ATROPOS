/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentEnvelopeTest {

    @Test
    fun `instantiates envelope data fields correctly`() {
        val env = IntentEnvelope("env_1", "status", mapOf("target" to "core"), true)
        assertEquals("status", env.command)
        assertTrue(env.parsedOk)
    }
}
