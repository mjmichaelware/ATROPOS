/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

import atropos.core.evaluation.EvidenceStore
import java.io.File
import java.nio.file.Path

class AmendmentRegistryTest {
    @Test
    fun testStoreAndRetrieveAmendment() {
        val tempDir = File.createTempFile("amendment", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val registry = AmendmentRegistry(store)
            
            val manifest = StructuralManifest("placeholder", listOf(
                ManifestRegion(RegionType.CODE, 0, 100, null)
            ))
            
            val (contentHash, manifestHash) = registry.registerAmendment("Amendment Content", manifest, "someOldHash")
            val retrievedManifest = registry.getAmendmentManifest(manifestHash)
            assertNotNull(retrievedManifest)
            assertEquals(contentHash, retrievedManifest?.documentHash)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testProtectedSourceDocOverwriteFails() {
        val tempDir = File.createTempFile("amendment", "dir").apply { delete(); mkdirs() }
        try {
            val store = EvidenceStore(repoRoot = Path.of(tempDir.absolutePath))
            val registry = AmendmentRegistry(store)
            
            val manifest = StructuralManifest("placeholder", emptyList())
            
            val exception = assertFailsWith<IllegalArgumentException> {
                registry.registerAmendment("Invalid", manifest, "sourceDoc1PlaceholderHash")
            }
            assertTrue(exception.message!!.contains("Cannot supersede or overwrite an original Source Doc hash"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
