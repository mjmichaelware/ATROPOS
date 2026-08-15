/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SourceDocumentRegistryTest {

    @Test
    fun `registry contains four source documents`() {
        val docs = SourceDocumentRegistry.getDocuments()
        assertEquals(4, docs.size)
        assertEquals("docs/source/ATROPOS_Source_Doc_1.txt", docs[0].path)
    }

    @Test
    fun `findCoordinate maps coordinate codes to source documents`() {
        val doc = SourceDocumentRegistry.findCoordinate("SD3-042")
        assertNotNull(doc)
        assertEquals("docs/source/ATROPOS_Source_Doc_3.txt", doc.path)

        val invalid = SourceDocumentRegistry.findCoordinate("XYZ-001")
        assertNull(invalid)
    }
}
