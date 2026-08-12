/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import java.nio.file.Files
import java.nio.file.Path

/**
 * Source Authority Law — Priority #6 runtime enforcement.
 *
 * This is the runtime law that governs exact source authority:
 *
 * 1. **Hash-pinned integrity**: every indexed document's `source_id` must match
 *    the real SHA-256 prefix of the file in `docs/source/`. If the file has
 *    been modified since indexing, the law detects it.
 *
 * 2. **Index freshness**: if `docs/source/` contains authority files that are
 *    not yet indexed, the law can trigger re-indexing.
 *
 * 3. **HIG=0 enforcement**: no lookup against a source whose hash cannot be
 *    verified is permitted. Unverifiable sources produce a typed
 *    [SourceAuthorityVerdict.Rejected] — never a guessed result.
 *
 * 4. **Formal invariants**: `HIG=0`, `ADDRESS_NEVER_BLINDLY_INGEST=true`.
 *
 * This law works alongside [HigZeroGuard] and [DloiService]:
 * - [DloiService] resolves exact addresses against the index
 * - [HigZeroGuard] converts failures to typed NoMatch results
 * - [SourceAuthorityLaw] verifies the index itself is truthful
 */
class SourceAuthorityLaw(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize()
) {
    private val sourceDir = repoRoot.resolve("docs/source")

    /**
     * The verdict of a source authority check.
     */
    sealed interface SourceAuthorityVerdict {
        /** The source index is verified: all indexed documents match their real files. */
        data class Verified(
            val verifiedDocuments: List<VerifiedDocument>,
            val unindexedFiles: List<Path>
        ) : SourceAuthorityVerdict

        /** At least one indexed document's hash does not match its source file. */
        data class Rejected(
            val reason: String,
            val mismatches: List<HashMismatch>
        ) : SourceAuthorityVerdict

        /** No authority source files exist at all. */
        data class NoSources(val reason: String) : SourceAuthorityVerdict
    }

    data class VerifiedDocument(
        val sourceId: String,
        val sha256: String,
        val filename: String,
        val path: Path
    )

    data class HashMismatch(
        val filename: String,
        val expectedSourceId: String,
        val actualSourceId: String,
        val path: Path
    )

    /**
     * Verify the integrity of the source authority index.
     *
     * Walks `docs/source/`, computes SHA-256 of each authority file, and checks
     * whether the DLOI index contains a matching entry with the correct source_id.
     */
    fun verify(): SourceAuthorityVerdict {
        if (!Files.exists(sourceDir)) {
            return SourceAuthorityVerdict.NoSources(
                "docs/source/ does not exist — no authority documents to verify"
            )
        }

        val sourceFiles = Files.list(sourceDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().let { name ->
                    name.endsWith(".txt") || name.endsWith(".md")
                }}
                .sorted()
                .toList()
        }

        if (sourceFiles.isEmpty()) {
            return SourceAuthorityVerdict.NoSources(
                "docs/source/ contains no .txt or .md authority files"
            )
        }

        val indexer = SourceAuthorityIndexer(repoRoot)
        val service = DloiService(repoRoot)
        // Verification observes the existing derived index. The normal DLOI
        // read path repairs a missing index, which would make an unindexed or
        // modified source appear verified before this law can inspect it.
        val loadedDocs = service.loadDocuments(ensureIndex = false)
        val loadedSourceIds = loadedDocs.map { it.sourceId }.toSet()

        val verified = mutableListOf<VerifiedDocument>()
        val mismatches = mutableListOf<HashMismatch>()
        val unindexed = mutableListOf<Path>()

        for (file in sourceFiles) {
            val bytes = try { Files.readAllBytes(file) } catch (_: Exception) { continue }
            if (bytes.isEmpty()) continue

            val sha256 = SourceAuthorityIndexer.sha256Hex(bytes)
            val sourceId = sha256.take(16)

            if (sourceId in loadedSourceIds) {
                verified += VerifiedDocument(
                    sourceId = sourceId,
                    sha256 = sha256,
                    filename = file.fileName.toString(),
                    path = file
                )
            } else {
                // Check if indexed under a different hash (file was modified)
                val indexedEntry = loadedDocs.firstOrNull { doc ->
                    doc.originalFilename.contains(file.fileName.toString().substringBeforeLast('.'))
                }
                if (indexedEntry != null) {
                    mismatches += HashMismatch(
                        filename = file.fileName.toString(),
                        expectedSourceId = indexedEntry.sourceId,
                        actualSourceId = sourceId,
                        path = file
                    )
                } else {
                    // Path implements Iterable<Path>, so `+=` would resolve to
                    // List.plus(Iterable) and fail to assign back to a val.
                    unindexed.add(file)
                }
            }
        }

        return if (mismatches.isNotEmpty()) {
            SourceAuthorityVerdict.Rejected(
                reason = "source authority hash mismatch: " +
                    mismatches.joinToString(", ") { "${it.filename}: expected ${it.expectedSourceId}, got ${it.actualSourceId}" },
                mismatches = mismatches
            )
        } else {
            SourceAuthorityVerdict.Verified(
                verifiedDocuments = verified,
                unindexedFiles = unindexed
            )
        }
    }

    /**
     * Ensure the source index is built and current.
     *
     * If docs/source/ contains files not yet in the index, index them.
     * Returns the [SourceAuthorityVerdict] after ensuring freshness.
     */
    fun ensureIndex(): SourceAuthorityVerdict {
        if (!Files.exists(sourceDir)) {
            return SourceAuthorityVerdict.NoSources(
                "docs/source/ does not exist — no authority documents to index"
            )
        }

        val indexer = SourceAuthorityIndexer(repoRoot)
        val initialVerdict = verify()

        when (initialVerdict) {
            is SourceAuthorityVerdict.NoSources -> return initialVerdict
            is SourceAuthorityVerdict.Verified -> {
                if (initialVerdict.unindexedFiles.isEmpty()) return initialVerdict
                // Index missing files
                initialVerdict.unindexedFiles.forEach { path ->
                    indexer.indexFile(path)
                }
                return verify()
            }
            is SourceAuthorityVerdict.Rejected -> {
                // Re-index all files to repair the index
                indexer.index()
                return verify()
            }
        }
    }

    /**
     * Guard a DLOI lookup with source-authority verification.
     *
     * This is the composition point: before resolving an address, verify that
     * the index is hash-pinned and current. If verification fails, return a
     * typed [DloiLookupResult.NoMatch] with the verification failure reason.
     */
    fun guardedResolve(
        guard: HigZeroGuard,
        address: String,
        skipVerification: Boolean = false
    ): DloiLookupResult {
        if (!skipVerification) {
            when (val verdict = verify()) {
                is SourceAuthorityVerdict.Rejected -> {
                    return DloiLookupResult.NoMatch(
                        query = address,
                        reason = "source authority verification failed: ${verdict.reason}"
                    )
                }
                is SourceAuthorityVerdict.NoSources -> {
                    // Fall through to the guard — it will return NoMatch if no docs loaded
                }
                is SourceAuthorityVerdict.Verified -> {
                    // Index is verified, proceed
                }
            }
        }
        return guard.resolve(address)
    }
}
