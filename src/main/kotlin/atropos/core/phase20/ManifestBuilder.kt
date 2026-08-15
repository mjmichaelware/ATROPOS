/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Utility for building structural manifests linearly as content is appended.
 */
class ManifestBuilder(private val documentHash: String) {
    private val regions = mutableListOf<ManifestRegion>()
    private var currentOffset: Long = 0L

    /**
     * Appends a new region by specifying its type, length in bytes, and an optional parent hash.
     */
    fun appendRegion(type: RegionType, byteLength: Long, parentHash: String? = null): ManifestBuilder {
        require(byteLength >= 0) { "Byte length cannot be negative" }
        val start = currentOffset
        val end = currentOffset + byteLength
        regions.add(ManifestRegion(type, start, end, parentHash))
        currentOffset = end + 1 // +1 for assumed newline or separator between regions
        return this
    }

    /**
     * Completes and constructs the structural manifest.
     */
    fun build(): StructuralManifest {
        return StructuralManifest(documentHash, regions.toList())
    }
}
