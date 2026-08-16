/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class DeletionProof(
    val objectId: String,
    val bytes: Long,
    val reason: String,
    val referenceCount: Int,
    val protected: Boolean
) {
    val allowed: Boolean get() = bytes >= 0 && referenceCount == 0 && !protected && reason.isNotBlank()
}

class DeletionProofBuilder {
    fun build(objectId: String, bytes: Long, reason: String, references: Int, protected: Boolean): DeletionProof =
        DeletionProof(objectId, bytes, reason, references, protected)
}
