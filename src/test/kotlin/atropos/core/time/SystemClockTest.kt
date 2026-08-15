// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.time

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemClockTest {
    @Test
    fun `real clock returns current time`() {
        val clock = RealClock()
        val before = Instant.now().toEpochMilli()
        val now = clock.now().toEpochMilli()
        val after = Instant.now().toEpochMilli()
        assertTrue(now in before..after)
    }

    @Test
    fun `test clock returns configured time`() {
        val start = Instant.parse("2026-08-15T00:00:00Z")
        val clock = TestClock(start)
        assertEquals(start, clock.now())

        val next = Instant.parse("2026-08-15T01:00:00Z")
        clock.setTime(next)
        assertEquals(next, clock.now())
    }
}
