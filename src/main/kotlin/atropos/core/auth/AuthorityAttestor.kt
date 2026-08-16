/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import atropos.core.verification.AuthorityAttestation
import java.nio.file.Files
import java.nio.file.Path

/**
 * Computes an authority document's fingerprint and compares it to the record.
 *
 * `SUP.AUTH.HASH-ATTEST`: "Mismatch → fail-closed + auditor finding + optional
 * recovery prompt."
 *
 * Split out from the loaders so that both [AgentsMdLoader] and [SwarmMdLoader]
 * attest identically. Two loaders each doing their own comparison is exactly
 * how one of them ends up with a subtly weaker check — and the weaker one is
 * then the way in.
 *
 * Nothing here decides what to do about a mismatch. It reports, and
 * [AuthBootstrap] refuses. Keeping the verdict and the consequence apart means
 * `atropos auth verify` can show the state of every document without any of
 * them being enforced as a side effect of looking.
 */
class AuthorityAttestor(
    private val store: FingerprintStore,
    private val repoRoot: Path
) {
    /**
     * Attests the document at [relativePath].
     *
     * On first sight the fingerprint is recorded and the document is
     * [AttestationResult.Attested] — there is nothing yet to have drifted from.
     * That is trust-on-first-use, and it is the correct boundary here: the
     * atom's predicate is that *silent mutation becomes detectable*, not that
     * the first author is authenticated, which no local hash can establish.
     */
    fun attest(relativePath: String, precedenceRank: Int): AttestationResult {
        val file = resolve(relativePath) ?: return AttestationResult.Missing(relativePath)
        if (!Files.isRegularFile(file)) return AttestationResult.Missing(relativePath)

        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull()
            ?: return AttestationResult.Missing(relativePath)

        val observed = AuthorityFingerprint(
            path = relativePath,
            sha256 = AuthorityAttestation.sha256(bytes.toString(Charsets.UTF_8)),
            sizeBytes = bytes.size.toLong(),
            modifiedEpochMillis = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L),
            loaderVersion = AuthorityFingerprint.LOADER_VERSION
        )

        val recorded = store.read(relativePath)
        if (recorded == null) {
            store.record(observed)
            return AttestationResult.Attested(
                AuthorityDocument(relativePath, observed.sha256, precedenceRank)
            )
        }

        if (!recorded.matches(observed)) {
            return AttestationResult.Mismatch(
                path = relativePath,
                expected = recorded.sha256,
                observed = observed.sha256
            )
        }

        // The bytes are unchanged but mtime may have moved. Re-recording keeps
        // the table describing the file as it is now, so a later size/mtime
        // pre-check is comparing against something current.
        if (recorded.modifiedEpochMillis != observed.modifiedEpochMillis) store.record(observed)

        return AttestationResult.Attested(
            AuthorityDocument(relativePath, observed.sha256, precedenceRank)
        )
    }

    /**
     * Accepts the document at [relativePath] as it now stands.
     *
     * The "optional recovery prompt" half of the atom. A legitimate edit to
     * `Agents.md` is an ordinary event, and the only alternative to an explicit
     * re-attestation is deleting the table by hand — which would re-attest
     * every document at once, including the one that was tampered with.
     *
     * Deliberately never called from a load path. Re-attestation that happened
     * automatically would make the whole gate ceremonial.
     */
    fun reattest(relativePath: String): Boolean {
        val file = resolve(relativePath) ?: return false
        if (!Files.isRegularFile(file)) return false
        val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return false
        return store.record(
            AuthorityFingerprint(
                path = relativePath,
                sha256 = ArtifactHasher.sha256Bytes(bytes),
                sizeBytes = bytes.size.toLong(),
                modifiedEpochMillis = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L),
                loaderVersion = AuthorityFingerprint.LOADER_VERSION
            )
        )
    }

    /** The file's bytes as text, or null when it cannot be read. */
    fun readText(relativePath: String): String? {
        val file = resolve(relativePath) ?: return null
        return runCatching { Files.readString(file) }.getOrNull()
    }

    /**
     * Resolves a repository-relative path, refusing anything that escapes.
     *
     * An authority document path reaches this from configuration, and
     * configuration is one of the things an attacker who can edit files can
     * edit. `../../elsewhere/Agents.md` must not become readable just because
     * it was named.
     */
    private fun resolve(relativePath: String): Path? {
        if (relativePath.isBlank()) return null
        val candidate = runCatching { repoRoot.resolve(relativePath).normalize() }.getOrNull() ?: return null
        if (!candidate.startsWith(repoRoot.normalize())) return null
        return candidate
    }
}
