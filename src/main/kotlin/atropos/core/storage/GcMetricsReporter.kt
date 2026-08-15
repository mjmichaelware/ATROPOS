/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

/**
 * ST-018: Records metrics for garbage collection passes.
 */
data class GcPassResult(
    val watermarkId: String,
    val timestamp: Instant,
    val objectsScanned: Int,
    val objectsDeleted: Int,
    val bytesFreed: Long,
    val durationMs: Long
)

class GcMetricsReporter {
    private val history = mutableListOf<GcPassResult>()

    fun recordPass(result: GcPassResult) {
        history.add(result)
    }

    fun getHistory(): List<GcPassResult> = history.toList()

    fun getTotalBytesFreed(): Long = history.sumOf { it.bytesFreed }
}
