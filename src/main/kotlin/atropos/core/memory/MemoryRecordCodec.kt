package atropos.core.memory

import atropos.core.json.JsonStringField

object MemoryRecordCodec {
    private val NUMBER_FIELD_PATTERNS = listOf("createdAtEpochMs", "schemaVersion")
        .associateWith { name -> Regex(""""$name"\s*:\s*([0-9]+)""") }
    private val BOOLEAN_FIELD_PATTERNS = mapOf(
        "redacted" to Regex(""""redacted"\s*:\s*(true|false)""")
    )
    private const val HEX = "0123456789abcdef"

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
            append("\"contentSha256\":\"").append(escape(record.contentSha256)).append("\",")
            append("\"failureSignature\":\"").append(escape(record.failureSignature.orEmpty())).append("\",")
            append("\"sourceCoordinate\":\"").append(escape(record.sourceCoordinate.orEmpty())).append("\",")
            append("\"authority\":\"").append(record.authority.name).append("\",")
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
            val contentSha256 = stringField(line, "contentSha256") ?: ""
            val failureSignature = stringField(line, "failureSignature")?.takeIf { it.isNotBlank() }
            val sourceCoordinate = stringField(line, "sourceCoordinate")?.takeIf { it.isNotBlank() }
            val authority = stringField(line, "authority")
                ?.let { runCatching { MemoryAuthority.valueOf(it) }.getOrNull() }
                ?: MemoryAuthority.OBSERVATION
            val schemaVersion = intField(line, "schemaVersion") ?: 1
            val redacted = booleanField(line, "redacted") ?: true

            val record = MemoryRecord(
                id = id,
                kind = kind,
                title = title,
                body = body,
                tags = tags,
                createdAtEpochMs = createdAt,
                subjectType = subjectType,
                subjectId = subjectId,
                contentSha256 = contentSha256,
                failureSignature = failureSignature,
                sourceCoordinate = sourceCoordinate,
                authority = authority,
                schemaVersion = schemaVersion,
                redacted = redacted
            )
            val expectedHash = if (schemaVersion >= 3) recordSha256(record) else contentSha256(
                title = title,
                body = body,
                tags = tags,
                subjectType = subjectType,
                subjectId = subjectId,
                sourceCoordinate = sourceCoordinate
            )
            if (schemaVersion >= 3 && contentSha256.isBlank()) return null
            if (schemaVersion >= 3 && !redacted) return null
            if (contentSha256.isNotBlank() && contentSha256 != expectedHash) return null
            record
        } catch (_: Exception) {
            null
        }
    }

    fun contentSha256(
        title: String,
        body: String,
        tags: List<String>,
        subjectType: String?,
        subjectId: String?,
        sourceCoordinate: String?
    ): String {
        val material = listOf(
            title,
            body,
            tags.joinToString(","),
            subjectType.orEmpty(),
            subjectId.orEmpty(),
            sourceCoordinate.orEmpty()
        ).joinToString("|")
        return sha256Hex(material)
    }

    fun recordSha256(record: MemoryRecord): String {
        val material = listOf(
            record.id,
            record.kind.name,
            record.title,
            record.body,
            record.tags.joinToString("\u001f"),
            record.createdAtEpochMs.toString(),
            record.subjectType.orEmpty(),
            record.subjectId.orEmpty(),
            record.failureSignature.orEmpty(),
            record.sourceCoordinate.orEmpty(),
            record.authority.name,
            record.schemaVersion.toString(),
            record.redacted.toString()
        ).joinToString("\u001e") { value -> "${value.length}:$value" }
        return sha256Hex(material)
    }

    /**
     * Reads a string field by scanning rather than matching.
     *
     * The pattern this replaces recursed once per character of the field, so a
     * record with a body of a few thousand characters threw StackOverflowError
     * instead of decoding. Both memory channels reported it as a soft failure
     * and the run continued with no recall at all -- see [JsonStringField].
     */
    private fun stringField(json: String, name: String): String? =
        JsonStringField.value(json, name)?.let { unescape(it) }

    private fun longField(json: String, name: String): Long? {
        return NUMBER_FIELD_PATTERNS[name]?.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun intField(json: String, name: String): Int? {
        return NUMBER_FIELD_PATTERNS[name]?.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun booleanField(json: String, name: String): Boolean? {
        return BOOLEAN_FIELD_PATTERNS[name]?.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun tagsField(json: String): List<String> {
        val raw = JsonStringField.arrayBody(json, "tags") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return JsonStringField.values(raw).map { unescape(it) }
    }

    private fun sha256Hex(material: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
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
