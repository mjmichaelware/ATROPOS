package atropos.core.memory

import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Optional on-disk vector index for source chunks.
 *
 * The JSONL memory store and DLOI/lexical lookup remain authoritative. This
 * adapter is an accelerator only: it refuses to create a database until the
 * sqlite-vec extension can be loaded, and it never shells through a command
 * string containing user data.
 */
class SqliteVecMemoryIndex(
    private val database: File,
    private val process: (List<String>, String) -> ProcessResult = ::runSqlite
) {

    fun index(
        chunks: List<MemorySourceChunk>,
        embeddings: Map<String, List<Float>>
    ): IndexResult {
        database.parentFile?.mkdirs()
        val invalidChunk = chunks.firstOrNull { it.sha256 != sha256(it.text) }
        if (invalidChunk != null) {
            return IndexResult(0, null, "chunk hash does not match chunk text at index ${invalidChunk.index}")
        }
        val selected = chunks.mapNotNull { chunk ->
            val vector = embeddings[chunk.sha256].orEmpty()
            if (vector.isEmpty()) null else chunk to vector
        }
        if (selected.isEmpty()) return IndexResult(0, null, "no embeddings supplied")

        val dimension = selected.first().second.size
        if (selected.any { it.second.size != dimension }) {
            return IndexResult(0, null, "embedding dimensions do not match")
        }
        if (selected.any { (_, vector) -> vector.any { value -> !value.isFinite() } }) {
            return IndexResult(0, null, "embedding contains a non-finite value")
        }
        val rowIds = selected.map { stableRowId(it.first.sha256) }
        if (rowIds.size != rowIds.distinct().size) {
            return IndexResult(0, null, "content hashes produced colliding sqlite row ids")
        }

        val sql = buildString {
            appendLine("SELECT load_extension('sqlite_vec');")
            appendLine(
                "CREATE VIRTUAL TABLE IF NOT EXISTS atropos_memory_vectors " +
                    "USING vec0(embedding float[$dimension] distance_metric=cosine);"
            )
            appendLine(
                "CREATE TABLE IF NOT EXISTS atropos_memory_vector_chunks " +
                    "(rowid INTEGER PRIMARY KEY, sha256 TEXT NOT NULL UNIQUE, " +
                    "chunk_index INTEGER NOT NULL, text TEXT NOT NULL);"
            )
            selected.forEachIndexed { index, (chunk, vector) ->
                val rowId = rowIds[index]
                appendLine(
                    "INSERT OR REPLACE INTO atropos_memory_vector_chunks " +
                        "(rowid, sha256, chunk_index, text) VALUES " +
                        "($rowId, ${sqlText(chunk.sha256)}, ${chunk.index}, ${sqlText(chunk.text)});"
                )
                appendLine(
                    "INSERT OR REPLACE INTO atropos_memory_vectors(rowid, embedding) " +
                        "VALUES ($rowId, ${sqlVector(vector)});"
                )
            }
        }
        val result = process(listOf("sqlite3", database.absolutePath), sql)
        return if (result.exitCode == 0) {
            IndexResult(selected.size, null, null)
        } else {
            IndexResult(0, result.exitCode, result.output.take(MAX_ERROR_CHARS))
        }
    }

    fun search(embedding: List<Float>, limit: Int = 10): List<VectorHit> {
        if (embedding.isEmpty() || embedding.any { !it.isFinite() }) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)
        val sql = buildString {
            appendLine("SELECT load_extension('sqlite_vec');")
            appendLine(
                "SELECT c.sha256, c.chunk_index, c.text, v.distance " +
                    "FROM atropos_memory_vectors v " +
                    "JOIN atropos_memory_vector_chunks c ON c.rowid = v.rowid " +
                    "WHERE v.embedding MATCH ${sqlText(sqlVector(embedding))} " +
                    "AND k = $safeLimit ORDER BY v.distance;"
            )
        }
        val result = process(listOf("sqlite3", database.absolutePath, "-separator", "\t"), sql)
        if (result.exitCode != 0) return emptyList()
        return result.output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split('\t', limit = 4)
                if (fields.size != 4) return@mapNotNull null
                val chunkSha256 = fields[0]
                val text = fields[2]
                if (!chunkSha256.matches(SHA256_PATTERN) || sha256(text) != chunkSha256.lowercase()) {
                    return@mapNotNull null
                }
                VectorHit(
                    chunkSha256 = chunkSha256,
                    chunkIndex = fields[1].toIntOrNull() ?: return@mapNotNull null,
                    text = text,
                    distance = fields[3].toDoubleOrNull() ?: return@mapNotNull null
                )
            }
            .take(safeLimit)
            .toList()
    }

    data class IndexResult(
        val indexedChunks: Int,
        val exitCode: Int?,
        val error: String?
    )

    data class VectorHit(
        val chunkSha256: String,
        val chunkIndex: Int,
        val text: String,
        val distance: Double
    )

    data class ProcessResult(val exitCode: Int, val output: String)

    private fun stableRowId(sha256: String): Long =
        sha256.take(15).toLongOrNull(16)?.let { (it and Long.MAX_VALUE).coerceAtLeast(1L) } ?: 1L

    private fun sqlText(value: String): String = "'${value.replace("'", "''").replace("\u0000", "")}'"

    private fun sqlVector(values: List<Float>): String =
        values.joinToString(prefix = "[", postfix = "]") { value ->
            require(value.isFinite()) { "embedding contains a non-finite value" }
            value.toString()
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_ERROR_CHARS = 4096
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")

        private fun runSqlite(command: List<String>, input: String): ProcessResult = runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            process.outputStream.use { it.write(input.toByteArray(StandardCharsets.UTF_8)) }
            val output = readBoundedOutput(process.inputStream)
            ProcessResult(process.waitFor(), output)
        }.getOrElse { ProcessResult(127, it.message.orEmpty()) }
    }

    private fun readBoundedOutput(input: java.io.InputStream): String {
        val buffer = ByteArray(1024)
        val retained = java.io.ByteArrayOutputStream(MAX_ERROR_CHARS)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (total < MAX_ERROR_CHARS) {
                val keep = (MAX_ERROR_CHARS - total).coerceAtMost(count)
                retained.write(buffer, 0, keep)
                total += keep
            }
        }
        return retained.toString(StandardCharsets.UTF_8.name())
    }
}
