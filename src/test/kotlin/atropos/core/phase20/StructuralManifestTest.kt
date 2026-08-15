/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.*

class StructuralManifestTest {

    @Test
    fun testSerializeAndDeserialize() {
        val regions = listOf(
            ManifestRegion(RegionType.HEADER, 0L, 50L, null),
            ManifestRegion(RegionType.PROSE, 51L, 200L, "parentHash123"),
            ManifestRegion(RegionType.CODE, 201L, 500L, "parentHash456")
        )
        val original = StructuralManifest("docHash789", regions)

        val serialized = original.serialize()
        assertTrue(serialized.contains("documentHash=docHash789"))
        assertTrue(serialized.contains("region=HEADER:0:50:"))
        assertTrue(serialized.contains("region=PROSE:51:200:parentHash123"))

        val deserialized = StructuralManifest.deserialize(serialized)
        assertEquals(original.documentHash, deserialized.documentHash)
        assertEquals(original.regions.size, deserialized.regions.size)
        assertEquals(original.regions[1].type, deserialized.regions[1].type)
        assertEquals(original.regions[1].startByteOffset, deserialized.regions[1].startByteOffset)
        assertEquals(original.regions[1].parentHash, deserialized.regions[1].parentHash)
    }

    @Test
    fun testInvalidOffsetsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ManifestRegion(RegionType.HEADER, -1L, 10L, null)
        }
        assertFailsWith<IllegalArgumentException> {
            ManifestRegion(RegionType.HEADER, 50L, 40L, null)
        }
    }
}
