/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

class LakehouseRetrieveTest {
    @Test
    fun testRetrieveContentAndRegion() {
        val tempDir = File.createTempFile("lakehouse", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val retrieve = LakehouseRetrieve(store)
            
            val content = "HELLO WORLD"
            val hash = store.put(content, EvidenceKind.RAW)
            
            val manifest = StructuralManifest(hash, listOf(
                ManifestRegion(RegionType.PROSE, 0, 4, null),
                ManifestRegion(RegionType.PROSE, 6, 10, null)
            ))
            
            val fullContent = retrieve.retrieveContent(hash)
            assertEquals("HELLO WORLD", fullContent)
            
            val region1 = retrieve.retrieveRegion(manifest, 0)
            assertEquals("HELLO", region1)
            
            val region2 = retrieve.retrieveRegion(manifest, 1)
            assertEquals("WORLD", region2)
            
            assertNull(retrieve.retrieveRegion(manifest, 2)) // Invalid index
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
