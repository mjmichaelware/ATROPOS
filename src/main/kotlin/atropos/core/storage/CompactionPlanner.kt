/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class CompactionPlan(val storageClass: String, val objectIds: List<String>, val bytes: Long)

class CompactionPlanner {
    fun plan(storageClass: String, objects: List<BlobObject>, maxBytes: Long): List<CompactionPlan> {
        require(storageClass.isNotBlank() && maxBytes > 0)
        val plans = mutableListOf<CompactionPlan>()
        var current = mutableListOf<BlobObject>()
        var bytes = 0L
        fun flush() {
            if (current.isNotEmpty()) plans += CompactionPlan(storageClass, current.map { it.id }, bytes)
            current = mutableListOf()
            bytes = 0L
        }
        objects.forEach { objectValue ->
            if (bytes + objectValue.sizeBytes > maxBytes && current.isNotEmpty()) flush()
            current += objectValue
            bytes += objectValue.sizeBytes
        }
        flush()
        return plans
    }
}
