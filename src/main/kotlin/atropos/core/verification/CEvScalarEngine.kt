package atropos.core.verification

import atropos.core.evaluation.EvidenceStore
import java.nio.file.Path

class CEvScalarEngine(
    private val evidenceStore: EvidenceStore = EvidenceStore()
) {
    fun evaluate(requiredHashes: List<String>): Int {
        for (hash in requiredHashes) {
            if (!evidenceStore.has(hash)) {
                return 0
            }
        }
        return 1
    }
}