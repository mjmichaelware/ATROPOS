/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

data class MarkSweepCandidate(val objectId: String, val bytes: Long, val reason: String)

class MarkSweepPlanner(
    private val references: ObjectReferenceGraph,
    private val leases: ObjectLeaseStore,
    private val pins: ObjectPinStore,
    private val legalHolds: LegalHoldStore
) {
    fun plan(objects: List<BlobObject>, now: Instant): List<MarkSweepCandidate> = objects.mapNotNull { objectValue ->
        val protectedObject = references.isReferenced(objectValue.id) ||
            leases.active(objectValue.id, now) != null ||
            pins.isPinned(objectValue.id) ||
            legalHolds.isHeld(objectValue.id)
        if (protectedObject) null else MarkSweepCandidate(objectValue.id, objectValue.sizeBytes, "unreferenced")
    }
}
