/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.storage

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
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

private const val DEFAULT_MAX_DELTA_OBJECTS = 1_024

class CloudLakehouseSyncEngine(
    private val storageDir: File =
        AtroposRepoRootLocator.resolve().resolve(".atropos/cas").toFile(),
    private val maxDeltaObjects: Int = DEFAULT_MAX_DELTA_OBJECTS
) {
    private val storagePath = storageDir.toPath().toAbsolutePath().normalize()

    init {
        require(maxDeltaObjects > 0) { "CAS delta object limit must be positive" }
        require(!hasSymbolicComponent(storagePath)) {
            "CAS storage root is a symbolic link: $storagePath"
        }
        Files.createDirectories(storagePath)
        require(!hasSymbolicComponent(storagePath) && storagePath.toRealPath() == storagePath) {
            "CAS storage root does not resolve to its configured path: $storagePath"
        }
    }

    fun storeContentAddressed(content: ByteArray): String {
        require(!hasSymbolicComponent(storagePath)) {
            "CAS storage root has a symbolic path component: $storagePath"
        }
        val hash = hashContent(content)
        val target = storagePath.resolve("$hash.bin").toFile()
        require(!Files.isSymbolicLink(target.toPath())) {
            "CAS object path is a symbolic link: $hash"
        }

        if (target.isFile && hashFile(target.toPath()) == hash) return hash

        val temp = File.createTempFile("cas_", ".tmp", storagePath.toFile())

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

        check(target.isFile && runCatching { hashFile(target.toPath()) == hash }.getOrDefault(false)) {
            "CAS write integrity verification failed for $hash"
        }
        return hash
    }

    fun retrieveContent(hash: String): ByteArray? {
        val normalizedHash = requireValidHash(hash)
        if (hasSymbolicComponent(storagePath)) return null

        val target = storagePath.resolve("$normalizedHash.bin").toFile()
        return if (target.isFile && !Files.isSymbolicLink(target.toPath())) {
            target.readBytes().takeIf { hashContent(it) == normalizedHash }
        } else {
            null
        }
    }

    /** Returns the requested CAS delta without reading unrelated objects. */
    fun missingHashes(hashes: Iterable<String>): List<String> = normalizeHashes(hashes)
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
        val requested = normalizeHashes(hashes)
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

    private fun hashFile(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasVerifiedObject(hash: String): Boolean {
        if (hasSymbolicComponent(storagePath)) return false
        val target = storagePath.resolve("$hash.bin").toFile()
        return target.isFile && !Files.isSymbolicLink(target.toPath()) &&
            runCatching { hashFile(target.toPath()) == hash }.getOrDefault(false)
    }

    private fun normalizeHashes(hashes: Iterable<String>): List<String> {
        val unique = linkedSetOf<String>()
        hashes.forEach { hash ->
            unique += requireValidHash(hash)
            require(unique.size <= maxDeltaObjects) {
                "CAS delta exceeds max object limit of $maxDeltaObjects"
            }
        }
        return unique.sorted()
    }

    private fun hasSymbolicComponent(path: Path): Boolean {
        var current: Path? = path
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }

    private fun requireValidHash(hash: String): String {
        require(hash.matches(Regex("[a-fA-F0-9]{64}"))) {
            "CAS hash must be a SHA-256 hex digest"
        }
        return hash.lowercase()
    }
}
