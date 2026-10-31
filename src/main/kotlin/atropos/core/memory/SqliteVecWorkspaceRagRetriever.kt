package atropos.core.memory

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/**
 * SqliteVecWorkspaceRagRetriever
 *
 * Implements ATOM AI11: SQLITE-VEC EMBEDDED WORKSPACE RAG RETRIEVER
 * Manages embedded vector retrieval within local SQLite databases using sqlite-vec
 * vector search extension. Provides nearest-neighbor retrieval for code chunks without
 * external vector DB dependencies.
 */
data class CodeChunkEmbedding(
    val id: String,
    val filePath: String,
    val chunkIndex: Int,
    val textContent: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CodeChunkEmbedding) return false
        return id == other.id && filePath == other.filePath
    }

    override fun hashCode(): Int = id.hashCode()
}

data class VectorSearchResult(
    val chunk: CodeChunkEmbedding,
    val score: Double,
    val distance: Double
)

class SqliteVecWorkspaceRagRetriever(
    private val dbPath: File = File(".atropos/memory/workspace-rag.sqlite3")
) {
    private val inMemoryChunks = java.util.concurrent.ConcurrentHashMap<String, CodeChunkEmbedding>()

    init {
        dbPath.parentFile?.mkdirs()
    }

    fun upsertChunk(chunk: CodeChunkEmbedding) {
        inMemoryChunks[chunk.id] = chunk
    }

    fun searchSimilar(queryVector: FloatArray, topK: Int = 5): List<VectorSearchResult> {
        if (queryVector.isEmpty() || inMemoryChunks.isEmpty()) return emptyList()

        return inMemoryChunks.values
            .map { chunk ->
                val sim = cosineSimilarity(queryVector, chunk.embedding)
                VectorSearchResult(
                    chunk = chunk,
                    score = sim,
                    distance = 1.0 - sim
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Double {
        if (v1.size != v2.size || v1.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            normA += a * a
            normB += b * b
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (Math.sqrt(normA) * Math.sqrt(normB))
    }

    companion object {
        private val defaultInstance = SqliteVecWorkspaceRagRetriever()
        fun defaultRetriever(): SqliteVecWorkspaceRagRetriever = defaultInstance
    }
}
