/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.json

object JsonStringField {

    fun value(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val opened = JsonParserHelpers.openingQuoteAfterKey(json, at + key.length)
            if (opened >= 0) {
                val closed = JsonParserHelpers.endOfString(json, opened + 1)
                if (closed >= 0) return json.substring(opened + 1, closed)
            }
            from = at + 1
        }
    }

    fun values(json: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < json.length) {
            if (json[index] == '"') {
                val closed = JsonParserHelpers.endOfString(json, index + 1)
                if (closed < 0) break
                found += json.substring(index + 1, closed)
                index = closed + 1
            } else {
                index++
            }
        }
        return found
    }

    fun arrayBody(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = JsonParserHelpers.skipToValue(json, at + key.length)
            if (index >= 0 && index < json.length && json[index] == '[') {
                return JsonParserHelpers.balanced(json, index, '[', ']')
            }
            from = at + 1
        }
    }

    fun objectBody(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = JsonParserHelpers.skipToValue(json, at + key.length)
            if (index >= 0 && index < json.length && json[index] == '{') {
                return JsonParserHelpers.balanced(json, index, '{', '}')
            }
            from = at + 1
        }
    }

    fun objectsIn(arrayBody: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < arrayBody.length) {
            when (arrayBody[index]) {
                '"' -> {
                    val closed = JsonParserHelpers.endOfString(arrayBody, index + 1)
                    if (closed < 0) return found
                    index = closed + 1
                }
                '{' -> {
                    val body = JsonParserHelpers.balanced(arrayBody, index, '{', '}') ?: return found
                    found += body
                    index += body.length + 2
                }
                else -> index++
            }
        }
        return found
    }

    fun text(json: String, name: String): String? = value(json, name)?.let(JsonParserHelpers::unescape)

    fun longValue(json: String, name: String): Long? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = JsonParserHelpers.skipToValue(json, at + key.length)
            if (index in 0 until json.length) {
                var end = index
                if (end < json.length && json[end] == '-') end++
                while (end < json.length && json[end].isDigit()) end++
                if (end > index) {
                    json.substring(index, end).toLongOrNull()?.let { return it }
                }
            }
            from = at + 1
        }
    }

    fun booleanValue(json: String, name: String): Boolean? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = JsonParserHelpers.skipToValue(json, at + key.length)
            if (index in 0 until json.length) {
                if (json.startsWith("true", index)) return true
                if (json.startsWith("false", index)) return false
            }
            from = at + 1
        }
    }

    fun unescape(raw: String): String = JsonParserHelpers.unescape(raw)
}
