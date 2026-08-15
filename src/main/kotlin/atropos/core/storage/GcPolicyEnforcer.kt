/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

/**
 * Enforces garbage collection policies against incoming objects using watermarks.
 */
class GcPolicyEnforcer(
    private val retentionRules: Map<String, StorageRetentionRule>
) {
    fun evaluateObject(
        objectId: String, 
        ruleId: String, 
        createdAt: Instant, 
        currentWatermark: GcWatermark
    ): Boolean {
        val rule = retentionRules[ruleId] 
            ?: throw IllegalArgumentException("Unknown retention rule ID: $ruleId")
            
        return rule.isEligibleForGc(createdAt, currentWatermark)
    }
    
    fun filterEligible(
        objects: List<Triple<String, String, Instant>>,
        currentWatermark: GcWatermark
    ): List<String> {
        return objects.filter { (_, ruleId, createdAt) ->
            val rule = retentionRules[ruleId]
            rule != null && rule.isEligibleForGc(createdAt, currentWatermark)
        }.map { it.first }
    }
}
