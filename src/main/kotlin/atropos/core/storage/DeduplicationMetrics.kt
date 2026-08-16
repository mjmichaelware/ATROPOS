/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class DeduplicationMetrics(val objects: Long, val uniqueObjects: Long, val bytes: Long, val uniqueBytes: Long) {
    val savedBytes: Long get() = (bytes - uniqueBytes).coerceAtLeast(0)
    val ratio: Double get() = if (bytes == 0L) 1.0 else uniqueBytes.toDouble() / bytes
}

class DeduplicationMetricsCalculator {
    fun calculate(objects: Iterable<Pair<String, Long>>): DeduplicationMetrics {
        val all = objects.toList()
        val unique = all.distinctBy { it.first }
        return DeduplicationMetrics(all.size.toLong(), unique.size.toLong(), all.sumOf { it.second }, unique.sumOf { it.second })
    }
}
