/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class CloudLakehouseSyncEngine(
    private val storageDir: File =
        File(System.getProperty("user.dir"), ".atropos/cas")
) {
    init {
        Files.createDirectories(storageDir.toPath())
    }

    fun storeContentAddressed(content: ByteArray): String {
        val hash = hashContent(content)
        val target = File(storageDir, "$hash.bin")

        if (target.exists()) return hash

        val temp = File.createTempFile("cas_", ".tmp", storageDir)

        try {
            temp.writeBytes(content)

            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temp.exists()) temp.delete()
        }

        return hash
    }

    fun retrieveContent(hash: String): ByteArray? {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) {
            "CAS hash must be a SHA-256 hex digest"
        }

        val target = File(storageDir, "$hash.bin")
        return if (target.exists() && target.isFile) {
            target.readBytes()
        } else {
            null
        }
    }

    private fun hashContent(content: ByteArray): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(content)

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }
}
