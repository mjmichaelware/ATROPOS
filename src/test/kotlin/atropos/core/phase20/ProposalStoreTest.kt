/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

class ProposalStoreTest {
    @Test
    fun testStoreAndRetrieveProposal() {
        val tempDir = File.createTempFile("proposal", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val ledger = ProposalStore(store)
            
            val manifest = StructuralManifest("placeholder", listOf(
                ManifestRegion(RegionType.HEADER, 0, 20, null)
            ))
            
            val (contentHash, manifestHash) = ledger.storeProposal("Proposal Content", manifest)
            
            val retrievedManifest = ledger.getProposalManifest(manifestHash)
            assertNotNull(retrievedManifest)
            assertEquals(contentHash, retrievedManifest?.documentHash)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
