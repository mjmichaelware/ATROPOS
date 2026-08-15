/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

import java.time.Instant

class GcSweeperTest {
    @Test
    fun testSweepFreesQuota() {
        val rule = StorageRetentionRule("rule-1", null, false)
        val enforcer = GcPolicyEnforcer(mapOf("rule-1" to rule))
        val tracker = StorageQuotaTracker(1000L)
        tracker.reserve(1000L)
        
        val sweeper = GcSweeper(enforcer, tracker) { id ->
            if (id == "obj1") 500L else 0L
        }
        
        // One instant for both watermark fields: two separate now() calls put
        // the boundary microseconds after the timestamp, which GcWatermark
        // rejects as a future boundary.
        val now = Instant.now()
        val candidates = listOf(Triple("obj1", "rule-1", now))
        val watermark = GcWatermark("wm", now, now, 0L)
        
        val freed = sweeper.sweep(candidates, watermark)
        assertEquals(500L, freed)
        assertEquals(500L, tracker.getUsage())
    }
}
