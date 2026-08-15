/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.security.MessageDigest

/**
 * ST-013: Ensures stored objects match their expected hashes, detecting bit rot or tampering.
 */
class StorageIntegrityChecker(private val driver: StorageDriver) {

    fun verifyChecksum(objectId: String, expectedSha256: String): Boolean {
        val blob = driver.read(objectId) ?: return false
        
        val digest = MessageDigest.getInstance("SHA-256")
        blob.contentStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash == expectedSha256
    }
}
