/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration
import java.time.Instant

/**
 * Defines retention policies for storage governance, determining if an object is eligible for GC.
 */
data class StorageRetentionRule(
    val ruleId: String,
    val maxAge: Duration?,
    val isPermanent: Boolean
) {
    fun isEligibleForGc(createdAt: Instant, currentWatermark: GcWatermark): Boolean {
        if (isPermanent) return false
        if (maxAge == null) return true // No age restriction, eligible immediately if not permanent
        
        val expiryTime = createdAt.plus(maxAge)
        return expiryTime.isBefore(currentWatermark.safeDeletionBoundary)
    }
}
