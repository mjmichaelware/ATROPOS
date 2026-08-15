/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.io.InputStream
import java.time.Instant

/**
 * ST-006: Core abstraction for a stored blob object, including metadata for GC and Quota.
 */
data class BlobObject(
    val id: String,
    val sizeBytes: Long,
    val createdAt: Instant,
    val retentionRuleId: String,
    val contentStream: () -> InputStream
) {
    init {
        require(id.isNotBlank()) { "Blob ID cannot be blank" }
        require(sizeBytes >= 0) { "Size in bytes cannot be negative" }
        require(retentionRuleId.isNotBlank()) { "Retention Rule ID cannot be blank" }
    }
}
