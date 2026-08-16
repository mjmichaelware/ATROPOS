/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.security.MessageDigest

/** Fail-closed precondition for creating a new authority amendment. */
class AmendmentGate(private val protectedHashes: Set<String> = emptySet()) {
    fun authorize(content: String, supersedesHash: String?, manifest: StructuralManifest): Boolean {
        if (content.isBlank() || supersedesHash.isNullOrBlank()) return false
        if (supersedesHash in protectedHashes) return false
        if (manifest.documentHash == supersedesHash) return false
        return sha256(content) != supersedesHash
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
