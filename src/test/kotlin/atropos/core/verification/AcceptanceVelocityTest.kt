/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptanceVelocityTest {

    @Test
    fun `calculates zero velocity for empty events`() {
        assertEquals(0.0, AcceptanceVelocity.calculate(emptyList()))
    }

    @Test
    fun `calculates correct velocity based on distinct verified predicates`() {
        val now = Instant.now()
        val events = listOf(
            VerificationEvent(now.minusSeconds(3600), "A001", true),
            VerificationEvent(now.minusSeconds(7200), "A002", true),
            VerificationEvent(now.minusSeconds(10800), "A001", true) // duplicate
        )
        // 2 distinct predicates over 24 hours (1 day) -> velocity = 2.0
        assertEquals(2.0, AcceptanceVelocity.calculate(events, 24))
    }
}
