/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind

/**
 * Stores raw evidence combined with its structural manifest to fulfill P20-LH01.
 */
class EvidenceLedger(private val store: EvidenceStore = EvidenceStore()) {
    /**
     * Stores the manifest. It assumes the actual document content has already been 
     * stored and verified, tying its regions to the document hash.
     */
    fun storeManifest(manifest: StructuralManifest): String {
        return store.put(manifest.serialize(), EvidenceKind.VERIFIER_FINDING)
    }

    /**
     * Retrieves the manifest for a given manifest hash.
     */
    fun getManifest(manifestHash: String): StructuralManifest? {
        val raw = store.get(manifestHash) ?: return null
        return try {
            StructuralManifest.deserialize(raw)
        } catch (e: Exception) {
            null
        }
    }
}
