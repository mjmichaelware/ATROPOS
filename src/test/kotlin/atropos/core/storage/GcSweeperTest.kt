/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
        
        val candidates = listOf(Triple("obj1", "rule-1", Instant.now()))
        val watermark = GcWatermark("wm", Instant.now(), Instant.now(), 0L)
        
        val freed = sweeper.sweep(candidates, watermark)
        assertEquals(500L, freed)
        assertEquals(500L, tracker.getUsage())
    }
}
