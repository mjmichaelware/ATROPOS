/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks current storage usage against governance quotas.
 */
class StorageQuotaTracker(
    val maxCapacityBytes: Long
) {
    private val currentUsageBytes = AtomicLong(0)
    
    init {
        require(maxCapacityBytes > 0) { "Max capacity must be positive" }
    }

    fun getUsage(): Long = currentUsageBytes.get()
    
    fun reserve(bytes: Long): Boolean {
        require(bytes >= 0) { "Cannot reserve negative bytes" }
        while (true) {
            val current = currentUsageBytes.get()
            if (current + bytes > maxCapacityBytes) {
                return false
            }
            if (currentUsageBytes.compareAndSet(current, current + bytes)) {
                return true
            }
        }
    }
    
    fun deleteUsage(bytes: Long) {
        release(bytes)
    }

    fun release(bytes: Long) {
        require(bytes >= 0) { "Cannot release negative bytes" }
        var updated: Long
        do {
            val current = currentUsageBytes.get()
            updated = maxOf(0L, current - bytes)
        } while (!currentUsageBytes.compareAndSet(current, updated))
    }
}
