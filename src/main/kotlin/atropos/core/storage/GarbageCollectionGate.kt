/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class GarbageCollectionDecision(val allowed: Boolean, val reason: String)

class GarbageCollectionGate {
    fun evaluate(constitution: StorageConstitution, reclaimBytes: Long): GarbageCollectionDecision {
        if (reclaimBytes < 0) return GarbageCollectionDecision(false, "reclaim size cannot be negative")
        if (reclaimBytes > constitution.reclaimableBytes()) {
            return GarbageCollectionDecision(false, "reclaim exceeds proven reclaimable bytes")
        }
        return GarbageCollectionDecision(true, "reclaim bounded by storage constitution")
    }
}
