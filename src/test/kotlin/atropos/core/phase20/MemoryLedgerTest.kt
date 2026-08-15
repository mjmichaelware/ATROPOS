/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

import atropos.core.evaluation.EvidenceStore
import java.io.File
import java.nio.file.Path

class MemoryLedgerTest {
    @Test
    fun testStoreAndRetrieveMemory() {
        val tempDir = File.createTempFile("memory", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val ledger = MemoryLedger(store)
            
            val manifest = StructuralManifest("placeholder", listOf(
                ManifestRegion(RegionType.PROSE, 0, 50, null)
            ))
            
            val (contentHash, manifestHash) = ledger.storeMemory("Test Memory Content", manifest)
            assertNotNull(contentHash)
            assertNotNull(manifestHash)
            
            val retrievedManifest = ledger.getMemoryManifest(manifestHash)
            assertNotNull(retrievedManifest)
            assertEquals(contentHash, retrievedManifest?.documentHash)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
