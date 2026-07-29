package atropos.dloi

class DloiJsonReader {
    fun string(json: String, field: String): String {
        val raw = rawValue(json, field)
            ?: error("missing DLOI string field: $field")
        if (!raw.startsWith('"')) error("null/unexpected DLOI string field: $field")
        return decodeString(raw.substring(1, raw.length - 1))
    }

    fun stringOrNull(json: String, field: String): String? {
        val raw = rawValue(json, field) ?: return null
        if (raw == "null") return null
        if (!raw.startsWith('"')) return null
        return decodeString(raw.substring(1, raw.length - 1))
    }

    fun int(json: String, field: String): Int =
        intOrNull(json, field) ?: error("missing DLOI int field: $field")

    fun intOrNull(json: String, field: String): Int? =
        rawValue(json, field)?.toIntOrNull()

    fun sectionsBlock(json: String): String {
        val marker = "\"sections\": ["
        val start = json.indexOf(marker)
        require(start >= 0) { "missing DLOI sections block" }
        val contentStart = start + marker.length
        var depth = 1
        var cursor = contentStart
        var inString = false
        while (cursor < json.length && depth > 0) {
            val ch = json[cursor]
            when {
                ch == '"' && cursor > 0 && json[cursor - 1] != '\\' -> inString = !inString
                !inString && ch == '[' -> depth += 1
                !inString && ch == ']' -> depth -= 1
            }
            cursor += 1
        }
        require(depth == 0) { "unterminated DLOI sections block" }
        return json.substring(contentStart, cursor - 1)
    }

    fun sectionObjects(sectionsBlock: String): Sequence<String> = sequence {
        var cursor = 0
        while (cursor < sectionsBlock.length) {
            val objStart = sectionsBlock.indexOf('{', cursor)
            if (objStart < 0) break
            var depth = 1
            var scan = objStart + 1
            var inString = false
            while (scan < sectionsBlock.length && depth > 0) {
                val ch = sectionsBlock[scan]
                when {
                    ch == '"' && scan > 0 && sectionsBlock[scan - 1] != '\\' -> inString = !inString
                    !inString && ch == '{' -> depth += 1
                    !inString && ch == '}' -> depth -= 1
                }
                scan += 1
            }
            if (depth != 0) break
            yield(sectionsBlock.substring(objStart, scan))
            cursor = scan
        }
    }

    private fun rawValue(json: String, field: String): String? {
        val fieldMarker = "\"$field\""
        var pos = json.indexOf(fieldMarker)
        if (pos < 0) return null
        pos = json.indexOf(':', pos + fieldMarker.length)
        if (pos < 0) return null
        pos++
        while (pos < json.length && json[pos].isWhitespace()) pos++
        if (pos >= json.length) return null
        val first = json[pos]
        return when {
            first == '"' -> stringValue(json, pos)
            first == 'n' && json.regionMatches(pos, "null", 0, 4) -> "null"
            first == '-' || first.isDigit() -> numberValue(json, pos)
            first == '{' -> balancedValue(json, pos, '{', '}')
            first == '[' -> balancedValue(json, pos, '[', ']')
            else -> null
        }
    }

    private fun stringValue(json: String, start: Int): String? {
        var pos = start + 1
        while (pos < json.length) {
            val ch = json[pos]
            if (ch == '\\') pos += 2
            else if (ch == '"') return json.substring(start, pos + 1)
            else pos++
        }
        return null
    }

    private fun numberValue(json: String, start: Int): String {
        var pos = start
        while (pos < json.length && (json[pos].isDigit() || json[pos] == '-' || json[pos] == '.')) pos++
        return json.substring(start, pos)
    }

    private fun balancedValue(json: String, start: Int, open: Char, close: Char): String? {
        var depth = 1
        var pos = start + 1
        while (pos < json.length && depth > 0) {
            val ch = json[pos]
            when {
                ch == '"' && pos > 0 && json[pos - 1] != '\\' -> {
                    pos++
                    while (pos < json.length) {
                        val nested = json[pos]
                        if (nested == '\\') pos += 2
                        else if (nested == '"') break
                        else pos++
                    }
                }
                ch == open -> depth++
                ch == close -> depth--
            }
            pos++
        }
        return if (depth == 0) json.substring(start, pos) else null
    }

    private fun decodeString(raw: String): String {
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char != '\\') {
                builder.append(char)
                index += 1
                continue
            }
            val escaped = raw.getOrNull(index + 1) ?: break
            when (escaped) {
                '\\' -> builder.append('\\')
                '"' -> builder.append('"')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val hex = raw.substring(index + 2, index + 6)
                    builder.append(hex.toInt(16).toChar())
                    index += 4
                }
                else -> builder.append(escaped)
            }
            index += 2
        }
        return builder.toString()
    }
}
