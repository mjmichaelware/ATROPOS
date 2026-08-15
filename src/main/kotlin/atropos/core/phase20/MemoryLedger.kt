/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind

/**
 * Persists and retrieves Memory snapshots using CAS, tying them to a StructuralManifest.
 */
class MemoryLedger(private val store: EvidenceStore = EvidenceStore()) {
    fun storeMemory(content: String, manifest: StructuralManifest): Pair<String, String> {
        val contentHash = store.put(content, EvidenceKind.RAW)
        val finalManifest = manifest.copy(documentHash = contentHash)
        val manifestHash = store.put(finalManifest.serialize(), EvidenceKind.METRIC_SNAPSHOT)
        return Pair(contentHash, manifestHash)
    }

    fun getMemoryManifest(manifestHash: String): StructuralManifest? {
        val raw = store.get(manifestHash) ?: return null
        return try {
            StructuralManifest.deserialize(raw)
        } catch (e: Exception) {
            null
        }
    }
}
