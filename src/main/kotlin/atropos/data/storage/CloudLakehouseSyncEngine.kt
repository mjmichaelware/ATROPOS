/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class CasDeltaSyncReport(
    val requested: List<String>,
    val alreadyPresent: List<String>,
    val imported: List<String>,
    val skipped: Map<String, String>
) {
    val complete: Boolean
        get() = skipped.isEmpty()
}

class CloudLakehouseSyncEngine(
    private val storageDir: File =
        AtroposRepoRootLocator.resolve().resolve(".atropos/cas").toFile()
) {
    init {
        Files.createDirectories(storageDir.toPath())
    }

    fun storeContentAddressed(content: ByteArray): String {
        val hash = hashContent(content)
        val target = File(storageDir, "$hash.bin")

        if (target.isFile && hashContent(target.readBytes()) == hash) return hash

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

        check(target.isFile && runCatching { hashContent(target.readBytes()) == hash }.getOrDefault(false)) {
            "CAS write integrity verification failed for $hash"
        }
        return hash
    }

    fun retrieveContent(hash: String): ByteArray? {
        val normalizedHash = requireValidHash(hash)

        val target = File(storageDir, "$normalizedHash.bin")
        return if (target.isFile) {
            target.readBytes().takeIf { hashContent(it) == normalizedHash }
        } else {
            null
        }
    }

    /** Returns the requested CAS delta without reading unrelated objects. */
    fun missingHashes(hashes: Iterable<String>): List<String> = hashes
        .map { requireValidHash(it) }
        .distinct()
        .filterNot(::hasVerifiedObject)

    /**
     * Lazily imports only requested hashes. The fetcher is an adapter for a
     * remote CAS; this owner remains transport-neutral and verifies every
     * returned byte before it can enter the local store.
     */
    fun syncDelta(
        hashes: Iterable<String>,
        fetch: (String) -> ByteArray?
    ): CasDeltaSyncReport {
        val requested = hashes.map { requireValidHash(it) }.distinct().sorted()
        val missing = missingHashes(requested).toSet()
        val alreadyPresent = requested.filterNot { it in missing }
        val imported = mutableListOf<String>()
        val skipped = linkedMapOf<String, String>()
        requested.filter { it in missing }.forEach { hash ->
            val content = runCatching { fetch(hash) }.getOrNull()
            when {
                content == null -> skipped[hash] = "remote content unavailable"
                hashContent(content) != hash.lowercase() -> skipped[hash] = "remote content hash mismatch"
                else -> {
                    val stored = runCatching { storeContentAddressed(content) }
                    if (stored.isSuccess) {
                        imported += hash
                    } else {
                        skipped[hash] = "local CAS write failed: ${stored.exceptionOrNull()?.javaClass?.simpleName ?: "unknown"}"
                    }
                }
            }
        }
        return CasDeltaSyncReport(requested, alreadyPresent, imported, skipped)
    }

    private fun hashContent(content: ByteArray): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(content)

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }

    private fun hasVerifiedObject(hash: String): Boolean {
        val target = File(storageDir, "$hash.bin")
        return target.isFile && runCatching { hashContent(target.readBytes()) == hash }.getOrDefault(false)
    }

    private fun requireValidHash(hash: String): String {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) {
            "CAS hash must be a SHA-256 hex digest"
        }
        return hash.lowercase()
    }
}
