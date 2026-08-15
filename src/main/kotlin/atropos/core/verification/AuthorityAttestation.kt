/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.security.MessageDigest

data class AttestationVerdict(
    val filename: String,
    val expectedHash: String,
    val observedHash: String,
    val matching: Boolean
)

object AuthorityAttestation {
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verify(filename: String, content: String, expectedHash: String): AttestationVerdict {
        val observed = sha256(content.trim())
        return AttestationVerdict(
            filename = filename,
            expectedHash = expectedHash,
            observedHash = observed,
            matching = (observed == expectedHash)
        )
    }
}
