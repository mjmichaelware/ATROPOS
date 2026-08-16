/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class Tombstone(val objectId: String, val deletedAt: Long, val proof: String) {
    init { require(objectId.isNotBlank() && proof.isNotBlank()) }
}

/** Records deletion intent and proof without erasing reclaim history. */
class TombstoneStore {
    private val tombstones = linkedMapOf<String, Tombstone>()

    fun record(tombstone: Tombstone) { tombstones[tombstone.objectId] = tombstone }

    fun find(objectId: String): Tombstone? = tombstones[objectId]

    fun contains(objectId: String): Boolean = objectId in tombstones

    fun snapshot(): List<Tombstone> = tombstones.values.toList()
}
