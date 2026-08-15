/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Structural manifest ensuring every CAS object carries its region types, byte offsets, and parent relationships.
 * Implements the P20-LH05 ledger requirement.
 */
data class StructuralManifest(
    val documentHash: String,
    val regions: List<ManifestRegion>
) {
    init {
        require(documentHash.isNotBlank()) { "Document hash cannot be blank" }
    }

    /**
     * Serializes the manifest into a deterministic string format suitable for storage.
     */
    fun serialize(): String {
        return buildString {
            appendLine("documentHash=$documentHash")
            regions.forEach { region ->
                val parentPart = region.parentHash ?: ""
                appendLine("region=${region.type.name}:${region.startByteOffset}:${region.endByteOffset}:$parentPart")
            }
        }
    }

    companion object {
        /**
         * Reconstructs a StructuralManifest from its serialized string representation.
         */
        fun deserialize(content: String): StructuralManifest {
            var docHash = ""
            val parsedRegions = mutableListOf<ManifestRegion>()
            
            for (line in content.lines()) {
                if (line.isBlank()) continue
                if (line.startsWith("documentHash=")) {
                    docHash = line.substringAfter("documentHash=")
                } else if (line.startsWith("region=")) {
                    val parts = line.substringAfter("region=").split(":")
                    if (parts.size >= 3) {
                        val type = RegionType.valueOf(parts[0])
                        val start = parts[1].toLong()
                        val end = parts[2].toLong()
                        val parentHash = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
                        parsedRegions.add(ManifestRegion(type, start, end, parentHash))
                    }
                }
            }
            
            require(docHash.isNotBlank()) { "Deserialized manifest missing documentHash" }
            return StructuralManifest(docHash, parsedRegions.toList())
        }
    }
}
