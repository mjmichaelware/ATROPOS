/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * The recorded integrity claim about one authority document.
 *
 * `SUP.AUTH.HASH-ATTEST` names the schema: "path, sha256, mtime, size, loader
 * version". Each field earns its place by catching a different way the claim
 * can go stale.
 *
 * [sha256] is the only field that decides trust. [size] and [mtime] are cheap
 * pre-checks — a file whose size changed cannot possibly hash the same, so the
 * common tampering case is caught without reading the bytes at all, which
 * matters on the phone-class storage this targets.
 *
 * [loaderVersion] exists because a change in how a document is *parsed* can
 * change what it means without changing a byte. When the loader's reading of
 * the same bytes changes, the old record no longer describes what will now be
 * believed, and the record must be re-established rather than silently reused.
 */
data class AuthorityFingerprint(
    /** Repository-relative, so a record survives the tree being moved. */
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
    val loaderVersion: Int
) {
    /**
     * Whether [other] describes the same bytes read by the same loader.
     *
     * Deliberately not `equals`: [modifiedEpochMillis] is excluded. Touching a
     * file, checking it out again, or copying the tree changes mtime while the
     * content is identical, and refusing to boot over that would make the gate
     * a nuisance rather than a control. The bytes are what was attested.
     */
    fun matches(other: AuthorityFingerprint): Boolean =
        path == other.path &&
            sha256 == other.sha256 &&
            sizeBytes == other.sizeBytes &&
            loaderVersion == other.loaderVersion

    /** The single line form used by [FingerprintStore]. */
    fun encode(): String =
        listOf(path, sha256, sizeBytes.toString(), modifiedEpochMillis.toString(), loaderVersion.toString())
            .joinToString("\t")

    companion object {
        /**
         * Bumped whenever a loader's interpretation of the same bytes changes.
         *
         * One shared number rather than one per loader: the loaders read the
         * same markdown grammar through [AuthorityMarkdownParser], so a change
         * to that grammar changes all of them at once.
         */
        const val LOADER_VERSION: Int = 1

        fun decode(line: String): AuthorityFingerprint? {
            val parts = line.split('\t')
            if (parts.size != 5) return null
            val size = parts[2].toLongOrNull() ?: return null
            val modified = parts[3].toLongOrNull() ?: return null
            val version = parts[4].toIntOrNull() ?: return null
            if (parts[0].isBlank() || parts[1].isBlank()) return null
            return AuthorityFingerprint(parts[0], parts[1], size, modified, version)
        }
    }
}
