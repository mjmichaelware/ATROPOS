/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/**
 * ST-007: Standard interface for all storage backend implementations.
 */
interface StorageDriver {
    val driverId: String
    fun write(blob: BlobObject): Boolean
    fun read(id: String): BlobObject?
    fun delete(id: String): Boolean
    fun listAllMetadata(): List<Triple<String, String, java.time.Instant>>
}
