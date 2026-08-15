/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore

/**
 * Unified retrieval surface for fetching raw lakehouse content using the documentHash from a structural manifest.
 */
class LakehouseRetrieve(private val store: EvidenceStore = EvidenceStore()) {
    fun retrieveContent(documentHash: String): String? {
        return store.get(documentHash)
    }

    fun retrieveRegion(manifest: StructuralManifest, regionIndex: Int): String? {
        val content = store.get(manifest.documentHash) ?: return null
        val region = manifest.regions.getOrNull(regionIndex) ?: return null
        val bytes = content.toByteArray(Charsets.UTF_8)
        
        if (region.startByteOffset > bytes.size || region.endByteOffset > bytes.size) {
            return null
        }
        
        val regionBytes = bytes.sliceArray(region.startByteOffset.toInt()..region.endByteOffset.toInt())
        return String(regionBytes, Charsets.UTF_8)
    }
}
