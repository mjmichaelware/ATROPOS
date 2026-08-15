/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

import java.time.Instant

class GcWatermarkTest {
    @Test
    fun testValidGcWatermark() {
        val now = Instant.now()
        val boundary = now.minusSeconds(3600)
        val watermark = GcWatermark("wm-001", now, boundary, 1024L)
        
        assertEquals("wm-001", watermark.watermarkId)
        assertEquals(now, watermark.timestamp)
        assertEquals(boundary, watermark.safeDeletionBoundary)
        assertEquals(1024L, watermark.enforcedBytes)
    }

    @Test
    fun testInvalidGcWatermark() {
        val now = Instant.now()
        val futureBoundary = now.plusSeconds(3600)
        
        assertFailsWith<IllegalArgumentException> {
            GcWatermark("", now, now, 100L)
        }
        
        assertFailsWith<IllegalArgumentException> {
            GcWatermark("wm-002", now, futureBoundary, 100L)
        }
        
        assertFailsWith<IllegalArgumentException> {
            GcWatermark("wm-003", now, now, -1L)
        }
    }
}
