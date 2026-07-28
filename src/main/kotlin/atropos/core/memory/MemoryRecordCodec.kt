package atropos.core.memory

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
            append("\"createdAtEpochMs\":").append(record.createdAtEpochMs).append(",")
            append("\"subjectType\":\"").append(escape(record.subjectType.orEmpty())).append("\",")
            append("\"subjectId\":\"").append(escape(record.subjectId.orEmpty())).append("\",")
            append("\"schemaVersion\":").append(record.schemaVersion).append(",")
            append("\"redacted\":").append(record.redacted)
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
            val subjectType = stringField(line, "subjectType")?.takeIf { it.isNotBlank() }
            val subjectId = stringField(line, "subjectId")?.takeIf { it.isNotBlank() }
            val schemaVersion = intField(line, "schemaVersion") ?: 1
            val redacted = booleanField(line, "redacted") ?: true
            MemoryRecord(id, kind, title, body, tags, createdAt, subjectType, subjectId, schemaVersion, redacted)
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

    private fun intField(json: String, name: String): Int? {
        val regex = Regex(""""$name"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun booleanField(json: String, name: String): Boolean? {
        val regex = Regex(""""$name"\s*:\s*(true|false)""")
        return regex.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
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
                i++
            }
        }
        return out.toString()
    }
}
