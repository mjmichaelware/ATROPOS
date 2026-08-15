/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Defines the fixed region types mandated by the P20-LH05 structural manifest discipline.
 */
enum class RegionType {
    HEADER, 
    SUBHEADER, 
    PROSE, 
    CODE, 
    LIST, 
    TABLE, 
    WARNING, 
    EXAMPLE, 
    ANTI_PATTERN
}

/**
 * Represents a single classified region within an evidence document.
 * Each region carries its type, byte bounds, and an optional parent hash to form a verifiable lineage tree.
 */
data class ManifestRegion(
    val type: RegionType,
    val startByteOffset: Long,
    val endByteOffset: Long,
    val parentHash: String?
) {
    init {
        require(startByteOffset >= 0) { "Start byte offset cannot be negative" }
        require(endByteOffset >= startByteOffset) { "End byte offset must be greater than or equal to start byte offset" }
        if (parentHash != null) {
            require(parentHash.isNotBlank()) { "Parent hash cannot be blank if provided" }
        }
    }
}
