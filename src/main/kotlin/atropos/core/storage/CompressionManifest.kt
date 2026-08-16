/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class CompressionRecord(val objectId: String, val originalBytes: Long, val storedBytes: Long, val codec: String) {
    init { require(objectId.isNotBlank() && originalBytes >= 0 && storedBytes >= 0 && codec.isNotBlank()) }
}

class CompressionManifest {
    private val records = linkedMapOf<String, CompressionRecord>()

    fun record(value: CompressionRecord) { records[value.objectId] = value }

    fun find(objectId: String): CompressionRecord? = records[objectId]

    fun snapshot(): List<CompressionRecord> = records.values.toList()
}
