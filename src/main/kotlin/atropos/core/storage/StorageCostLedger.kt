/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

data class StorageCostEntry(val storageClass: String, val bytes: Long, val observedAt: Instant) {
    init { require(storageClass.isNotBlank() && bytes >= 0) }
}

class StorageCostLedger {
    private val entries = mutableListOf<StorageCostEntry>()

    fun record(entry: StorageCostEntry) { entries += entry }

    fun snapshot(): List<StorageCostEntry> = entries.toList()

    fun bytesFor(storageClass: String): Long = entries.filter { it.storageClass == storageClass }.sumOf { it.bytes }
}
