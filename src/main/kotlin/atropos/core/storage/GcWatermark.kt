/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

/**
 * SD5#F05: Represents a Garbage Collection watermark defining safe deletion boundaries.
 */
data class GcWatermark(
    val watermarkId: String,
    val timestamp: Instant,
    val safeDeletionBoundary: Instant,
    val enforcedBytes: Long
) {
    init {
        require(watermarkId.isNotBlank()) { "Watermark ID cannot be blank" }
        require(safeDeletionBoundary.isBefore(timestamp) || safeDeletionBoundary == timestamp) { 
            "Safe deletion boundary cannot be in the future relative to the watermark timestamp" 
        }
        require(enforcedBytes >= 0) { "Enforced bytes cannot be negative" }
    }
}
