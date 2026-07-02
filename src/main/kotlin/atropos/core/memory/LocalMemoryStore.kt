package atropos.core.memory

import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class MemoryKind {
    NOTE,
    CODE,
    ROUTE,
    FAILURE,
    SOURCE,
    DECISION
}

data class MemoryRecord(
    val id: String,
    val kind: MemoryKind,
    val title: String,
    val body: String,
    val tags: List<String>,
    val createdAtEpochMs: Long
)

data class MemorySearchHit(
    val record: MemoryRecord,
    val score: Int
)

data class MemoryStatus(
    val root: File,
    val jsonlFile: File,
    val totalRecords: Int,
    val sqliteAvailable: Boolean,
    val sqliteVecAvailable: Boolean,
    val pineconeConfigured: Boolean,
    val supabaseConfigured: Boolean,
    val googleMetadataConfigured: Boolean
)

class LocalMemoryStore(
    private val root: File = File(".atropos/memory"),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val env: Map<String, String> = System.getenv()
) {
    private val jsonlFile = File(root, "memory.jsonl")

    fun remember(
        kind: MemoryKind,
        title: String,
        body: String,
        tags: List<String> = emptyList()
    ): MemoryRecord {
        root.mkdirs()
        val cleanedTitle = title.trim().ifEmpty { kind.name.lowercase(Locale.US) }
        val cleanedBody = body.trim()
        val cleanedTags = tags.map { it.trim().lowercase(Locale.US) }.filter { it.isNotEmpty() }.distinct()
        val createdAt = now()
        val id = stableId(kind, cleanedTitle, cleanedBody, createdAt)
        val record = MemoryRecord(id, kind, cleanedTitle, cleanedBody, cleanedTags, createdAt)
        jsonlFile.appendText(MemoryRecordCodec.encode(record) + "\n")
        return record
    }

    fun all(limit: Int = 200): List<MemoryRecord> {
        val safeLimit = limit.coerceIn(1, 1000)
        if (!jsonlFile.exists()) return emptyList()
        val decoded = jsonlFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { MemoryRecordCodec.decode(it) }
        return decoded.takeLast(safeLimit)
    }

    fun search(query: String, limit: Int = 20): List<MemorySearchHit> {
        val terms = query.lowercase(Locale.US)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<MemorySearchHit>()
        val records = all(1000)
        for (record in records) {
            val haystack = (
                record.title + "\n" +
                    record.body + "\n" +
                    record.tags.joinToString(" ")
                ).lowercase(Locale.US)

            var score = 0
            for (term in terms) {
                if (record.title.lowercase(Locale.US).contains(term)) score += 5
                if (record.tags.any { it.contains(term) }) score += 4
                if (record.body.lowercase(Locale.US).contains(term)) score += 2
                if (haystack.contains(term)) score += 1
            }
            if (score > 0) hits += MemorySearchHit(record, score)
        }

        return hits.sortedWith(
            compareByDescending<MemorySearchHit> { it.score }
                .thenByDescending { it.record.createdAtEpochMs }
        ).take(limit.coerceIn(1, 100))
    }

    fun status(): MemoryStatus {
        root.mkdirs()
        return MemoryStatus(
            root = root,
            jsonlFile = jsonlFile,
            totalRecords = all(1000).size,
            sqliteAvailable = commandExists("sqlite3"),
            sqliteVecAvailable = sqliteVecAvailable(),
            pineconeConfigured = env["PINECONE_API_KEY"].isNullOrBlank().not(),
            supabaseConfigured = env["SUPABASE_URL"].isNullOrBlank().not() &&
                env["SUPABASE_ANON_KEY"].isNullOrBlank().not(),
            googleMetadataConfigured = env["GOOGLE_APPLICATION_CREDENTIALS"].isNullOrBlank().not() ||
                env["GOOGLE_OAUTH_CLIENT_SECRET"].isNullOrBlank().not()
        )
    }

    private fun stableId(kind: MemoryKind, title: String, body: String, createdAt: Long): String {
        val material = "${kind.name}|$title|$body|$createdAt"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    private fun commandExists(name: String): Boolean {
        return try {
            val process = ProcessBuilder("sh", "-c", "command -v $name >/dev/null 2>&1")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun sqliteVecAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(
                "sh",
                "-c",
                "command -v sqlite3 >/dev/null 2>&1 && sqlite3 ':memory:' \"select load_extension('sqlite_vec');\" >/dev/null 2>&1"
            ).redirectErrorStream(true).start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}

object MemoryRecordCodec {
    fun encode(record: MemoryRecord): String {
        return buildString {
            append("{")
            append("\"id\":\"").append(escape(record.id)).append("\",")
            append("\"kind\":\"").append(record.kind.name).append("\",")
            append("\"title\":\"").append(escape(record.title)).append("\",")
            append("\"body\":\"").append(escape(record.body)).append("\",")
            append("\"tags\":[")
            record.tags.forEachIndexed { index, tag ->
                if (index > 0) append(",")
                append("\"").append(escape(tag)).append("\"")
            }
            append("],")
            append("\"createdAtEpochMs\":").append(record.createdAtEpochMs)
            append("}")
        }
    }

    fun decode(line: String): MemoryRecord? {
        return try {
            val id = stringField(line, "id") ?: return null
            val kind = MemoryKind.valueOf(stringField(line, "kind") ?: return null)
            val title = stringField(line, "title") ?: ""
            val body = stringField(line, "body") ?: ""
            val tags = tagsField(line)
            val createdAt = longField(line, "createdAtEpochMs") ?: 0L
            MemoryRecord(id, kind, title, body, tags, createdAt)
        } catch (_: Exception) {
            null
        }
    }

    private fun stringField(json: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.get(1)?.let { unescape(it) }
    }

    private fun longField(json: String, name: String): Long? {
        val regex = Regex(""""$name"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun tagsField(json: String): List<String> {
        val regex = Regex(""""tags"\s*:\s*\[(.*?)\]""")
        val raw = regex.find(json)?.groupValues?.get(1) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(raw)
            .map { unescape(it.groupValues[1]) }
            .toList()
    }

    private fun escape(value: String): String {
        return buildString {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }
}
