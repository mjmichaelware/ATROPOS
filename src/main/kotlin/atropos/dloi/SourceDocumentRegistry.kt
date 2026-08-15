/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

data class SourceDocument(
    val path: String,
    val sha256: String,
    val bytes: Int
)

object SourceDocumentRegistry {
    private val inventory = listOf(
        SourceDocument("docs/source/ATROPOS_Source_Doc_1.txt", "36ee88a1187a329875372ce53d4934c5c6e9d13ec96909e0f17d13ae341eba52", 69691),
        SourceDocument("docs/source/ATROPOS_Source_Doc_2.txt", "b88acd252ce5100c56e9b2902dcacce6e5f47600c30da2129317d10ca5991b07", 130292),
        SourceDocument("docs/source/ATROPOS_Source_Doc_3.txt", "b1a115a5253c5bff44b766ef18c3af99d8c210bce6c97dfe637b3e933ff36158", 71068),
        SourceDocument("docs/source/ATROPOS_Source_Doc_4.txt", "b2dc4fd9141307184b7a77ed83ebcb226071ebedeba680014281c84727cddace", 47912)
    )

    fun getDocuments(): List<SourceDocument> = inventory

    fun findCoordinate(coordinate: String): SourceDocument? {
        return when {
            coordinate.startsWith("SD1") -> inventory[0]
            coordinate.startsWith("SD2") -> inventory[1]
            coordinate.startsWith("SD3") -> inventory[2]
            coordinate.startsWith("SD4") -> inventory[3]
            else -> null
        }
    }

    private val metadataMap = mutableMapOf<String, DocumentMetadata>()
    private val hashToMetadataMap = mutableMapOf<String, DocumentMetadata>()

    fun register(id: String, name: String, version: String, hash: String, size: Long) {
        val metadata = DocumentMetadata(id, name, version, hash, size)
        metadataMap[id] = metadata
        hashToMetadataMap[hash] = metadata
    }

    fun getByHash(hash: String): DocumentMetadata? = hashToMetadataMap[hash]

    fun getById(id: String): DocumentMetadata? = metadataMap[id]
}

data class DocumentMetadata(
    val id: String,
    val name: String,
    val version: String,
    val hash: String,
    val size: Long
)
