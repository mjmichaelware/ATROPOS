/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.*
import atropos.testing.assertArrayEquals

import java.io.ByteArrayInputStream
import java.time.Instant

class BlobObjectTest {
    @Test
    fun testBlobObjectCreation() {
        val streamProvider = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        val blob = BlobObject("blob-1", 3L, Instant.now(), "rule-default", streamProvider)
        
        assertEquals("blob-1", blob.id)
        assertEquals(3L, blob.sizeBytes)
        assertEquals("rule-default", blob.retentionRuleId)
        assertArrayEquals(byteArrayOf(1, 2, 3), blob.contentStream().readBytes())
    }
}
