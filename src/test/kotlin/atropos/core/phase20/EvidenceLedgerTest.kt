/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

import atropos.core.evaluation.EvidenceStore
import java.io.File
import java.nio.file.Path

class EvidenceLedgerTest {
    @Test
    fun testStoreAndRetrieveManifest() {
        val tempDir = File.createTempFile("evidence", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val ledger = EvidenceLedger(store)
            
            val manifest = StructuralManifest("docHash1", listOf(
                ManifestRegion(RegionType.HEADER, 0, 100, null)
            ))
            
            val hash = ledger.storeManifest(manifest)
            assertNotNull(hash)
            
            val retrieved = ledger.getManifest(hash)
            assertNotNull(retrieved)
            assertEquals("docHash1", retrieved?.documentHash)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
