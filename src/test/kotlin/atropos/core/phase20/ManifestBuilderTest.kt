/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ManifestBuilderTest {

    @Test
    fun testBuilderComputesOffsets() {
        val builder = ManifestBuilder("testDocHash")
        builder.appendRegion(RegionType.HEADER, 10L)
        builder.appendRegion(RegionType.PROSE, 20L, "parentA")
        
        val manifest = builder.build()
        assertEquals(2, manifest.regions.size)
        
        val r1 = manifest.regions[0]
        assertEquals(RegionType.HEADER, r1.type)
        assertEquals(0L, r1.startByteOffset)
        assertEquals(10L, r1.endByteOffset)
        assertNull(r1.parentHash)
        
        val r2 = manifest.regions[1]
        assertEquals(RegionType.PROSE, r2.type)
        assertEquals(11L, r2.startByteOffset)
        assertEquals(31L, r2.endByteOffset)
        assertEquals("parentA", r2.parentHash)
    }
}
