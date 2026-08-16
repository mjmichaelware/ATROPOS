/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.artifact.ArtifactHasher

data class AttestationVerdict(
    val filename: String,
    val expectedHash: String,
    val observedHash: String,
    val matching: Boolean
)

object AuthorityAttestation {
    fun sha256(text: String): String {
        return ArtifactHasher.sha256Bytes(text.toByteArray(Charsets.UTF_8))
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
