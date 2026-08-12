package atropos.core.memory

import atropos.core.policy.BoundedProcessRunner
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Optional on-disk vector index for source chunks.
 *
 * The JSONL memory store and DLOI/lexical lookup remain authoritative. This
 * adapter is an accelerator only: it refuses to create a database until the
 * sqlite-vec extension can be loaded, and it never shells through a command
 * string containing user data.
 */
private const val DEFAULT_MAX_INDEX_CHUNKS = 1_024

class SqliteVecMemoryIndex(
    private val database: File,
    private val process: (List<String>, String) -> ProcessResult = ::runSqlite,
    private val maxIndexChunks: Int = DEFAULT_MAX_INDEX_CHUNKS
) {
    private val databasePath = database.toPath().toAbsolutePath().normalize()

    init {
        require(maxIndexChunks > 0) { "sqlite-vec chunk limit must be positive" }
    }

    fun index(
        chunks: List<MemorySourceChunk>,
        embeddings: Map<String, List<Float>>
    ): IndexResult {
        if (chunks.size > maxIndexChunks) {
            return IndexResult(0, null, "source vector index exceeds max chunk limit of $maxIndexChunks")
        }
        prepareDatabasePath()?.let { return IndexResult(0, null, it) }
        val invalidChunk = chunks.firstOrNull { it.sha256 != sha256(it.text) }
        if (invalidChunk != null) {
            return IndexResult(0, null, "chunk hash does not match chunk text at index ${invalidChunk.index}")
        }
        val selected = chunks.mapNotNull { chunk ->
            val vector = embeddings[chunk.sha256].orEmpty()
            if (vector.isEmpty()) null else chunk to vector
        }.distinctBy { it.first.sha256 }
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

        val preflight = buildString {
            appendLine("SELECT load_extension('sqlite_vec');")
            appendLine(
                "CREATE TABLE IF NOT EXISTS atropos_memory_vector_chunks " +
                    "(rowid INTEGER PRIMARY KEY, sha256 TEXT NOT NULL UNIQUE, " +
                    "chunk_index INTEGER NOT NULL, text TEXT NOT NULL);"
            )
            selected.forEachIndexed { index, (chunk, _) ->
                appendLine(
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM atropos_memory_vector_chunks " +
                        "WHERE rowid = ${rowIds[index]} AND sha256 <> ${sqlText(chunk.sha256)}) " +
                        "THEN '${ROW_ID_CONFLICT_PREFIX}${rowIds[index]}' ELSE '' END;"
                )
            }
        }
        val preflightResult = process(listOf("sqlite3", databasePath.toString()), preflight)
        if (preflightResult.exitCode != 0) {
            return IndexResult(0, preflightResult.exitCode, preflightResult.output.take(MAX_ERROR_CHARS))
        }
        if (preflightResult.output.lineSequence().any { it.startsWith(ROW_ID_CONFLICT_PREFIX) }) {
            return IndexResult(0, null, "content hash collides with an existing sqlite row id")
        }

        val sql = buildString {
            appendLine(".bail on")
            appendLine("BEGIN IMMEDIATE;")
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
            appendLine("COMMIT;")
        }
        val result = process(listOf("sqlite3", databasePath.toString()), sql)
        return if (result.exitCode == 0) {
            IndexResult(selected.size, null, null)
        } else {
            IndexResult(0, result.exitCode, result.output.take(MAX_ERROR_CHARS))
        }
    }

    fun search(embedding: List<Float>, limit: Int = 10): List<VectorHit> {
        if (embedding.isEmpty() || embedding.any { !it.isFinite() }) return emptyList()
        if (prepareDatabasePath(createParent = false) != null) return emptyList()
        val safeLimit = limit.coerceIn(1, 100)
        val sql = buildString {
            appendLine("SELECT load_extension('sqlite_vec');")
            appendLine(
                // Hex keeps the tab-separated transport unambiguous when a
                // source chunk itself contains tabs or line-oriented control
                // characters.
                "SELECT c.sha256, c.chunk_index, hex(c.text), v.distance " +
                    "FROM atropos_memory_vectors v " +
                    "JOIN atropos_memory_vector_chunks c ON c.rowid = v.rowid " +
                    "WHERE v.embedding MATCH ${sqlText(sqlVector(embedding))} " +
                    "AND k = $safeLimit ORDER BY v.distance;"
            )
        }
        val result = process(listOf("sqlite3", databasePath.toString(), "-separator", "\t"), sql)
        if (result.exitCode != 0) return emptyList()
        return result.output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val fields = line.split('\t', limit = 4)
                if (fields.size != 4) return@mapNotNull null
                val chunkSha256 = fields[0]
                val text = decodeHex(fields[2]) ?: return@mapNotNull null
                if (!chunkSha256.matches(SHA256_PATTERN) || sha256(text) != chunkSha256.lowercase()) {
                    return@mapNotNull null
                }
                val chunkIndex = fields[1].toIntOrNull() ?: return@mapNotNull null
                if (chunkIndex < 0) return@mapNotNull null
                val distance = fields[3].toDoubleOrNull() ?: return@mapNotNull null
                if (!distance.isFinite()) return@mapNotNull null
                VectorHit(
                    chunkSha256 = chunkSha256,
                    chunkIndex = chunkIndex,
                    text = text,
                    distance = distance
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

    private fun prepareDatabasePath(createParent: Boolean = true): String? {
        if (Files.isSymbolicLink(databasePath)) return "sqlite database path is a symbolic link"
        val parent = databasePath.parent ?: return "sqlite database path has no parent"
        if (hasSymbolicComponent(parent)) return "sqlite database path contains a symbolic ancestor"
        if (createParent && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            runCatching { Files.createDirectories(parent) }
                .onFailure { return "sqlite database parent creation failed" }
        }
        if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) return "sqlite database parent is unavailable"
        if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            return "sqlite database parent is not a real directory"
        }
        return runCatching {
            require(parent.toRealPath() == parent) {
                "sqlite database parent resolves outside its configured path"
            }
            null
        }.getOrElse { it.message ?: "sqlite database parent is not stable" }
    }

    private fun hasSymbolicComponent(path: java.nio.file.Path): Boolean {
        var cursor: java.nio.file.Path? = path.toAbsolutePath().normalize()
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) return true
            cursor = cursor.parent
        }
        return false
    }

    private fun sqlText(value: String): String = "'${value.replace("'", "''").replace("\u0000", "")}'"

    private fun sqlVector(values: List<Float>): String =
        values.joinToString(prefix = "[", postfix = "]") { value ->
            require(value.isFinite()) { "embedding contains a non-finite value" }
            value.toString()
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun decodeHex(value: String): String? {
        if (value.isEmpty() || value.length % 2 != 0 || !value.all { it in "0123456789abcdefABCDEF" }) {
            return null
        }
        val bytes = ByteArray(value.length / 2)
        value.chunked(2).forEachIndexed { index, pair ->
            bytes[index] = pair.toInt(16).toByte()
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    companion object {
        private const val MAX_ERROR_CHARS = 4096
        private const val SQLITE_TIMEOUT_MILLIS = 5_000L
        private const val SQLITE_TIMEOUT_EXIT = 124
        private const val ROW_ID_CONFLICT_PREFIX = "ATROPOS_ROW_ID_CONFLICT:"
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")

        private fun runSqlite(command: List<String>, input: String): ProcessResult = runCatching {
            val directory = Path.of(command.firstOrNull()?.let { File(it).parent } ?: ".")
                .toAbsolutePath().normalize().let { if (Files.isDirectory(it)) it else Path.of("/") }
            val result = BoundedProcessRunner().run(
                command = command,
                directory = directory,
                timeoutMillis = SQLITE_TIMEOUT_MILLIS,
                maxOutputBytes = MAX_ERROR_CHARS,
                maxOutputLines = 1_000,
                standardInput = input.toByteArray(StandardCharsets.UTF_8)
            )
            val output = result.stdout + result.stderr
            when {
                result.timedOut -> ProcessResult(SQLITE_TIMEOUT_EXIT, output)
                result.launchError != null -> ProcessResult(127, result.launchError)
                else -> ProcessResult(result.exitCode ?: 127, output)
            }
        }.getOrElse { ProcessResult(127, it.message.orEmpty()) }
    }

}
