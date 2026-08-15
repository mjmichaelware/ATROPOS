/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class GcPolicyEnforcerTest {
    @Test
    fun testEnforceGcPolicies() {
        val rule1 = StorageRetentionRule("rule-1", Duration.ofDays(5), false)
        val rule2 = StorageRetentionRule("rule-2", null, true)
        
        val enforcer = GcPolicyEnforcer(mapOf("rule-1" to rule1, "rule-2" to rule2))
        val boundary = Instant.now().minus(Duration.ofDays(1))
        val watermark = GcWatermark("wm", Instant.now(), boundary, 0L)
        
        val obj1CreatedAt = Instant.now().minus(Duration.ofDays(10)) // Eligible
        val obj2CreatedAt = Instant.now().minus(Duration.ofDays(2))  // Not eligible
        
        assertTrue(enforcer.evaluateObject("obj1", "rule-1", obj1CreatedAt, watermark))
        assertFalse(enforcer.evaluateObject("obj2", "rule-1", obj2CreatedAt, watermark))
        assertFalse(enforcer.evaluateObject("obj3", "rule-2", obj1CreatedAt, watermark))
        
        val batch = listOf(
            Triple("obj1", "rule-1", obj1CreatedAt),
            Triple("obj2", "rule-1", obj2CreatedAt),
            Triple("obj3", "rule-2", obj1CreatedAt)
        )
        
        val eligible = enforcer.filterEligible(batch, watermark)
        assertEquals(listOf("obj1"), eligible)
    }
}
