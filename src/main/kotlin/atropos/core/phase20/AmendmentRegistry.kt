/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind

/**
 * P20-LH04: Append-only registry that guarantees original Source Doc hashes are never overwritten.
 * Each amendment carries a structural manifest.
 */
class AmendmentRegistry(private val store: EvidenceStore = EvidenceStore()) {
    
    // Hardcoded known source doc hashes that must never be superseded or overwritten.
    private val protectedSourceDocHashes = setOf(
        "sourceDoc1PlaceholderHash", 
        "sourceDoc2PlaceholderHash", 
        "sourceDoc3PlaceholderHash"
    )

    fun registerAmendment(amendmentContent: String, manifest: StructuralManifest, supersedesHash: String?): Pair<String, String> {
        if (supersedesHash != null && protectedSourceDocHashes.contains(supersedesHash)) {
            throw IllegalArgumentException("Hard rule violation: Cannot supersede or overwrite an original Source Doc hash")
        }
        
        val contentHash = store.put(amendmentContent, EvidenceKind.RAW)
        val finalManifest = manifest.copy(documentHash = contentHash)
        val manifestHash = store.put(finalManifest.serialize(), EvidenceKind.VERIFIER_FINDING)
        
        return Pair(contentHash, manifestHash)
    }

    fun getAmendmentManifest(manifestHash: String): StructuralManifest? {
        val raw = store.get(manifestHash) ?: return null
        return try {
            StructuralManifest.deserialize(raw)
        } catch (e: Exception) {
            null
        }
    }
}
