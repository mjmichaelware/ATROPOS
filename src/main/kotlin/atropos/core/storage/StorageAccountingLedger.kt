/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

data class StorageAccountingEntry(
    val objectId: String,
    val bytes: Long,
    val storageClass: String,
    val recordedAt: Instant
) {
    init {
        require(objectId.isNotBlank())
        require(bytes >= 0)
        require(storageClass.isNotBlank())
    }
}

/** Append-only accounting view used by storage governance and evidence. */
class StorageAccountingLedger {
    private val entries = linkedMapOf<String, StorageAccountingEntry>()

    fun record(entry: StorageAccountingEntry) {
        entries[entry.objectId] = entry
    }

    fun remove(objectId: String): StorageAccountingEntry? = entries.remove(objectId)

    fun entry(objectId: String): StorageAccountingEntry? = entries[objectId]

    fun snapshot(): List<StorageAccountingEntry> = entries.values.toList()

    fun totalBytes(): Long = entries.values.sumOf { it.bytes }
}
