/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class StorageRetentionRuleTest {
    @Test
    fun testPermanentRuleNeverEligible() {
        val rule = StorageRetentionRule("rule-perm", Duration.ofDays(1), true)
        val createdAt = Instant.now().minus(Duration.ofDays(10))
        val watermark = GcWatermark("wm-1", Instant.now(), Instant.now(), 0L)
        
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
