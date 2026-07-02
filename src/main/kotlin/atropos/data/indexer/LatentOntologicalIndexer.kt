package atropos.data.indexer

import java.io.File
import java.io.Serializable
import kotlin.math.sqrt

data class SemanticVectorRecord(
    val chunkId: String,
    val textContent: String,
    val taxonomicCode: String,
    val embeddingVector: List<Double>
) : Serializable

class LatentOntologicalIndexer(private val dbStoragePath: String) {
    private val memoryIndexedCache = mutableListOf<SemanticVectorRecord>()

    /**
     * Compute cosine similarity between two high-dimensional arrays natively.
     * Bypasses the need for native aarch64 C-compiled matrix math library binaries.
     */
    fun computeCosineSimilarity(vectorA: List<Double>, vectorB: List<Double>): Double {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0.0
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    /**
     * Commits a structural data payload chunk to the vector ledger matrix.
     */
    fun insertIndexRecord(record: SemanticVectorRecord) {
        memoryIndexedCache.add(record)
        // Persistence fallback hook: Append entry straight down to a local storage flat ledger
        try {
            val persistentFlatFile = File(dbStoragePath)
            persistentFlatFile.parentFile?.mkdirs()
            persistentFlatFile.appendText(
                "${record.chunkId}|${record.taxonomicCode}|${record.embeddingVector.joinToString(",")}\n"
            )
        } catch (e: Exception) {
            // Safe fallback logging channel to protect system event loop states
        }
    }

    /**
     * Scans the active repository index context block to extract the most contextually 
     * relevant fragments matching the query constraints.
     */
    fun queryTopMatches(queryVector: List<Double>, limit: Int = 3): List<SemanticVectorRecord> {
        return memoryIndexedCache.asSequence()
            .map { record -> record to computeCosineSimilarity(queryVector, record.embeddingVector) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }
}
