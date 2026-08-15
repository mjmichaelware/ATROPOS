/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * ST-008: Local disk implementation of StorageDriver.
 */
class LocalDiskStorageDriver(
    private val rootDirectory: File
) : StorageDriver {
    override val driverId: String = "local-disk"

    init {
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }
        require(rootDirectory.isDirectory) { "Root must be a directory" }
    }

    override fun write(blob: BlobObject): Boolean {
        return try {
            val file = File(rootDirectory, blob.id)
            blob.contentStream().use { input ->
                Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun read(id: String): BlobObject? {
        val file = File(rootDirectory, id)
        if (!file.exists()) return null
        
        return BlobObject(
            id = id,
            sizeBytes = file.length(),
            createdAt = Instant.ofEpochMilli(file.lastModified()),
            retentionRuleId = "default-rule",
            contentStream = { file.inputStream() }
        )
    }

    override fun delete(id: String): Boolean {
        val file = File(rootDirectory, id)
        return if (file.exists()) file.delete() else false
    }

    override fun listAllMetadata(): List<Triple<String, String, Instant>> {
        val files = rootDirectory.listFiles() ?: return emptyList()
        return files.map { file ->
            Triple(file.name, "default-rule", Instant.ofEpochMilli(file.lastModified()))
        }
    }
}
