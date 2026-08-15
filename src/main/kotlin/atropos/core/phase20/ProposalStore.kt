/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind

/**
 * Persists and retrieves self-improvement Proposals using CAS.
 * Enforces that a proposal carries a valid structural manifest.
 */
class ProposalStore(private val store: EvidenceStore = EvidenceStore()) {
    fun storeProposal(proposalContent: String, manifest: StructuralManifest): Pair<String, String> {
        val contentHash = store.put(proposalContent, EvidenceKind.RAW)
        val finalManifest = manifest.copy(documentHash = contentHash)
        val manifestHash = store.put(finalManifest.serialize(), EvidenceKind.METRIC_SNAPSHOT)
        return Pair(contentHash, manifestHash)
    }

    fun getProposalManifest(manifestHash: String): StructuralManifest? {
        val raw = store.get(manifestHash) ?: return null
        return try {
            StructuralManifest.deserialize(raw)
        } catch (e: Exception) {
            null
        }
    }
}
