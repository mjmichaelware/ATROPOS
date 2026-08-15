/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/**
 * Coordinates the GC policy enforcer and the quota tracker to clean up storage.
 */
class GcSweeper(
    private val enforcer: GcPolicyEnforcer,
    private val quotaTracker: StorageQuotaTracker,
    private val objectDeleter: (String) -> Long // Returns bytes freed
) {
    fun sweep(
        candidates: List<Triple<String, String, java.time.Instant>>, 
        watermark: GcWatermark
    ): Long {
        val eligibleIds = enforcer.filterEligible(candidates, watermark)
        var totalFreed = 0L
        
        for (id in eligibleIds) {
            try {
                val freed = objectDeleter(id)
                quotaTracker.release(freed)
                totalFreed += freed
            } catch (e: Exception) {
                // Skip failed deletions
            }
        }
        
        return totalFreed
    }
}
