/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.security.MessageDigest

/** Fail-closed precondition for creating a new authority amendment. */
class AmendmentGate(private val protectedHashes: Set<String> = emptySet()) {
    fun authorize(content: String, supersedesHash: String?, manifest: StructuralManifest): Boolean =
        refusal(content, supersedesHash, manifest) == null

    /**
     * Why this amendment may not be registered, or null when it may.
     *
     * A fail-closed gate that can only say "no" makes its caller invent a
     * reason, and the four things checked here are not interchangeable: an
     * operator who superseded a protected Source Doc hash needs to be told
     * that, not that something in their request was invalid.
     */
    fun refusal(content: String, supersedesHash: String?, manifest: StructuralManifest): String? = when {
        content.isBlank() -> "Amendment content is empty"
        supersedesHash.isNullOrBlank() -> "An amendment must name the authority it supersedes"
        supersedesHash in protectedHashes ->
            "Cannot supersede or overwrite an original Source Doc hash"
        manifest.documentHash == supersedesHash ->
            "An amendment's manifest cannot claim the hash it supersedes"
        sha256(content) == supersedesHash ->
            "An amendment cannot supersede itself"
        else -> null
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
