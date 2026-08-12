/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Where the recorded fingerprints of authority documents live.
 *
 * `SUP.AUTH.HASH-ATTEST`: "On first successful load write record; every
 * subsequent load recompute and compare."
 *
 * Two properties this file exists to hold, both borrowed deliberately from
 * [atropos.core.worktree.WorktreeRecordStore] rather than invented again:
 *
 * **Writes are atomic.** The whole table is written to a sibling temp file and
 * moved into place. A crash part-way through leaves the previous table intact.
 * A truncated fingerprint table is worse than none at all — it would attest
 * some documents and silently forget others, and the forgotten ones would be
 * re-recorded on next boot as though their current contents had always been
 * authorised.
 *
 * **A path can never escape the store.** Records are keyed by
 * repository-relative path and the store writes exactly one file; nothing here
 * turns an untrusted string into a filename.
 *
 * The table is a small flat file rather than one file per document, because
 * the whole set is read on every boot and a directory scan on phone-class
 * storage costs more than reading a few hundred bytes.
 */
class FingerprintStore(private val tableFile: Path) {

    /** Every recorded fingerprint, keyed by repository-relative path. */
    fun readAll(): Map<String, AuthorityFingerprint> {
        if (!Files.isRegularFile(tableFile)) return emptyMap()
        val lines = runCatching { Files.readAllLines(tableFile, StandardCharsets.UTF_8) }
            .getOrNull() ?: return emptyMap()
        return lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(AuthorityFingerprint::decode)
            .associateBy { it.path }
    }

    fun read(path: String): AuthorityFingerprint? = readAll()[path]

    /**
     * Records [fingerprint], replacing any earlier record for the same path.
     *
     * @return false when the record could not be durably written. The caller
     *   must treat that as an un-attested document rather than assuming the
     *   write succeeded — an unrecorded fingerprint means the next boot has
     *   nothing to compare against.
     */
    fun record(fingerprint: AuthorityFingerprint): Boolean {
        val merged = readAll().toMutableMap()
        merged[fingerprint.path] = fingerprint
        return writeAll(merged.values.sortedBy { it.path })
    }

    /** Drops the record for [path], so the next load re-establishes it. */
    fun forget(path: String): Boolean {
        val merged = readAll().toMutableMap()
        if (merged.remove(path) == null) return true
        return writeAll(merged.values.sortedBy { it.path })
    }

    private fun writeAll(records: Collection<AuthorityFingerprint>): Boolean = runCatching {
        val parent = tableFile.parent
        if (parent != null) Files.createDirectories(parent)
        val body = buildString {
            appendLine("# atropos authority fingerprints")
            appendLine("# path\tsha256\tsize\tmtime\tloaderVersion")
            records.forEach { appendLine(it.encode()) }
        }
        val temp = Files.createTempFile(parent ?: tableFile.toAbsolutePath().parent, "fingerprints", ".tmp")
        Files.write(temp, body.toByteArray(StandardCharsets.UTF_8))
        runCatching {
            Files.move(temp, tableFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            // Some Android and network mounts cannot move atomically. A
            // non-atomic replace still beats writing in place, which would
            // leave a half-written table on a crash.
            Files.move(temp, tableFile, StandardCopyOption.REPLACE_EXISTING)
        }.getOrThrow()
        true
    }.getOrDefault(false)
}
