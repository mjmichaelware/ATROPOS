/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

class StorageQuotaTrackerTest {
    @Test
    fun testReserveAndRelease() {
        val tracker = StorageQuotaTracker(1000L)
        
        assertTrue(tracker.reserve(500L))
        assertEquals(500L, tracker.getUsage())
        
        assertTrue(tracker.reserve(500L))
        assertEquals(1000L, tracker.getUsage())
        
        assertFalse(tracker.reserve(1L))
        assertEquals(1000L, tracker.getUsage())
        
        tracker.release(200L)
        assertEquals(800L, tracker.getUsage())
        
        tracker.release(1000L) // Release more than used
        assertEquals(0L, tracker.getUsage())
    }
}
