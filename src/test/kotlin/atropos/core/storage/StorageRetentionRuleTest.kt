/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*

import java.time.Duration
import java.time.Instant

class StorageRetentionRuleTest {
    @Test
    fun testPermanentRuleNeverEligible() {
        val rule = StorageRetentionRule("rule-perm", Duration.ofDays(1), true)
        // One instant for both watermark fields: two separate now() calls put
        // the boundary microseconds after the timestamp, which GcWatermark
        // rejects as a future boundary.
        val now = Instant.now()
        val createdAt = now.minus(Duration.ofDays(10))
        val watermark = GcWatermark("wm-1", now, now, 0L)
        
        assertFalse(rule.isEligibleForGc(createdAt, watermark))
    }

    @Test
    fun testAgeBasedEligibility() {
        val rule = StorageRetentionRule("rule-age", Duration.ofDays(7), false)
        val createdAt = Instant.now().minus(Duration.ofDays(10))
        val boundary = Instant.now().minus(Duration.ofDays(1))
        val watermark = GcWatermark("wm-2", Instant.now(), boundary, 0L)
        
        // createdAt (10 days ago) + maxAge (7 days) = 3 days ago
        // Boundary is 1 day ago. 3 days ago is before 1 day ago, so it IS eligible.
        assertTrue(rule.isEligibleForGc(createdAt, watermark))
        
        val recentCreatedAt = Instant.now().minus(Duration.ofDays(5))
        // recentCreatedAt (5 days ago) + maxAge (7 days) = 2 days in the future
        // Boundary is 1 day ago. Not eligible.
        assertFalse(rule.isEligibleForGc(recentCreatedAt, watermark))
    }
}
