/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

/**
 * Small bounded parser for the MCP config subset owned by [McpHostManager].
 * It tracks quoted strings and balanced arrays/objects so command arguments or
 * descriptions containing braces cannot truncate the servers[] catalog.
 */
internal object McpConfigParser {
    fun parse(text: String): List<McpServerConfig> {
        val serversRaw = rawMember(text, "servers") ?: return emptyList()
        require(serversRaw.trimStart().startsWith("[")) { "mcp.json servers must be an array" }
        val body = serversRaw.trim().let { extractBalanced(it, 0, '[', ']') }
        return objectValues(body).mapNotNull { objectText ->
            val name = stringMember(objectText, "name") ?: return@mapNotNull null
            val args = rawMember(objectText, "args")?.let(::stringValues).orEmpty()
            McpServerConfig(
                name = name,
                transport = stringMember(objectText, "transport") ?: "stdio",
                command = stringMember(objectText, "command"),
                args = args,
                enabled = booleanMember(objectText, "enabled") ?: false,
                community = booleanMember(objectText, "community") ?: true,
                url = stringMember(objectText, "url")
            )
        }
    }

    private fun objectValues(arrayText: String): List<String> {
        val values = mutableListOf<String>()
        var index = 1
        while (index < arrayText.length - 1) {
            index = skipWhitespaceAndCommas(arrayText, index)
            if (index >= arrayText.length - 1) break
            require(arrayText[index] == '{') { "mcp.json servers entries must be objects" }
            val end = matchingEnd(arrayText, index, '{', '}')
            values += arrayText.substring(index, end + 1)
            index = end + 1
        }
        return values
    }

    private fun stringValues(arrayText: String): List<String> {
        val values = mutableListOf<String>()
        var index = 1
        while (index < arrayText.length - 1) {
            index = skipWhitespaceAndCommas(arrayText, index)
            if (index >= arrayText.length - 1) break
            require(arrayText[index] == '"') { "mcp.json args must contain strings" }
            val end = stringEnd(arrayText, index)
            values += decode(arrayText.substring(index, end + 1))
            index = end + 1
        }
        return values
    }

    private fun stringMember(text: String, name: String): String? =
        rawMember(text, name)?.let(::decode)

    private fun booleanMember(text: String, name: String): Boolean? =
        rawMember(text, name)?.trim()?.takeIf { it == "true" || it == "false" }?.toBoolean()

    private fun rawMember(text: String, name: String): String? {
        var index = skipWhitespace(text, 0)
        require(index < text.length && text[index] == '{') { "mcp.json object expected" }
        var objectDepth = 0
        var arrayDepth = 0
        while (index < text.length) {
            when (text[index]) {
                '"' -> {
                    val end = stringEnd(text, index)
                    if (objectDepth == 1 && arrayDepth == 0) {
                        val key = decode(text.substring(index, end + 1))
                        val colon = skipWhitespace(text, end + 1)
                        if (colon < text.length && text[colon] == ':') {
                            val valueStart = skipWhitespace(text, colon + 1)
                            if (key == name) return rawValue(text, valueStart)
                        }
                    }
                    index = end + 1
                }
                '{' -> {
                    objectDepth++
                    index++
                }
                '}' -> {
                    objectDepth--
                    index++
                }
                '[' -> {
                    arrayDepth++
                    index++
                }
                ']' -> {
                    arrayDepth--
                    index++
                }
                else -> index++
            }
        }
        return null
    }

    private fun rawValue(text: String, start: Int): String {
        var index = start
        require(index < text.length) { "mcp.json field has no value" }
        return when (text[index]) {
            '"' -> text.substring(index, stringEnd(text, index) + 1)
            '[', '{' -> {
                val close = if (text[index] == '[') ']' else '}'
                text.substring(index, matchingEnd(text, index, text[index], close) + 1)
            }
            else -> {
                val start = index
                while (index < text.length && text[index] !in ",}") index++
                text.substring(start, index).trim()
            }
        }
    }

    private fun decode(raw: String): String {
        val value = raw.trim()
        require(value.length >= 2 && value.first() == '"' && value.last() == '"') {
            "mcp.json string field is not quoted"
        }
        val result = StringBuilder()
        var index = 1
        while (index < value.length - 1) {
            val current = value[index]
            if (current != '\\') {
                result.append(current)
                index++
                continue
            }
            require(index + 1 < value.length - 1) { "mcp.json string has an incomplete escape" }
            when (val escaped = value[index + 1]) {
                '"', '\\', '/' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                else -> require(escaped == 'u' && index + 5 < value.length) {
                    "mcp.json string has an unsupported escape"
                }.also {
                    result.append(value.substring(index + 2, index + 6).toInt(16).toChar())
                    index += 4
                }
            }
            index += 2
        }
        return result.toString()
    }

    private fun extractBalanced(text: String, start: Int, open: Char, close: Char): String =
        text.substring(start, matchingEnd(text, start, open, close) + 1)

    private fun matchingEnd(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var index = start
        var quoted = false
        var escaped = false
        while (index < text.length) {
            val current = text[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (current == '\\') escaped = true
                else if (current == '"') quoted = false
            } else when (current) {
                '"' -> quoted = true
                open -> depth++
                close -> if (--depth == 0) return index
            }
            index++
        }
        error("mcp.json has an unterminated $open...$close value")
    }

    private fun stringEnd(text: String, start: Int): Int {
        var index = start + 1
        var escaped = false
        while (index < text.length) {
            when {
                escaped -> escaped = false
                text[index] == '\\' -> escaped = true
                text[index] == '"' -> return index
            }
            index++
        }
        error("mcp.json has an unterminated string")
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun skipWhitespaceAndCommas(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index].isWhitespace() || text[index] == ',')) index++
        return index
    }
}
