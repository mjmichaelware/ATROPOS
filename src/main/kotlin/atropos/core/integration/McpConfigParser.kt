/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.util.ArrayDeque

/**
 * Small bounded parser for the MCP config subset owned by [McpHostManager].
 * It tracks quoted strings and balanced arrays/objects so command arguments or
 * descriptions containing braces cannot truncate the servers[] catalog.
 */
internal object McpConfigParser {
    fun requireJsonObject(raw: String): String {
        val text = raw.trim()
        require(text.isNotEmpty() && text.first() == '{' && text.last() == '}') {
            "MCP tool arguments must be a JSON object"
        }
        val delimiters = ArrayDeque<Char>()
        var quoted = false
        var escaped = false
        text.forEachIndexed { index, ch ->
            if (quoted) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> quoted = false
                }
                return@forEachIndexed
            }
            when (ch) {
                '"' -> quoted = true
                '{', '[' -> delimiters.addLast(ch)
                '}', ']' -> {
                    require(delimiters.isNotEmpty()) { "MCP tool arguments have unbalanced delimiters" }
                    val expected = if (ch == '}') '{' else '['
                    require(delimiters.removeLast() == expected) {
                        "MCP tool arguments have mismatched delimiters"
                    }
                    require(index == text.lastIndex || delimiters.isNotEmpty()) {
                        "MCP tool arguments contain trailing JSON content"
                    }
                }
            }
        }
        require(!quoted && !escaped && delimiters.isEmpty()) {
            "MCP tool arguments have an incomplete JSON envelope"
        }
        return text
    }

    fun parse(text: String): List<McpServerConfig> {
        val rootStart = skipWhitespace(text, 0)
        require(rootStart < text.length && text[rootStart] == '{') { "mcp.json root must be an object" }
        val rootEnd = matchingEnd(text, rootStart, '{', '}')
        require(text.substring(rootEnd + 1).isBlank()) { "mcp.json has trailing content" }
        require(!hasTopLevelTrailingComma(text.substring(rootStart, rootEnd + 1))) {
            "mcp.json root object cannot have a trailing comma"
        }
        val serversRaw = rawMember(text, "servers") ?: return emptyList()
        require(serversRaw.trimStart().startsWith("[")) { "mcp.json servers must be an array" }
        val body = serversRaw.trim().let { extractBalanced(it, 0, '[', ']') }
        val servers = objectValues(body).map { objectText ->
            require(!hasTopLevelTrailingComma(objectText)) {
                "mcp.json server object cannot have a trailing comma"
            }
            val name = stringMember(objectText, "name")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: error("mcp.json server name is required")
            val args = rawMember(objectText, "args")?.let(::stringValues).orEmpty()
            val environment = rawMember(objectText, "env")?.let(::stringMap).orEmpty()
            McpServerConfig(
                name = name,
                transport = stringMember(objectText, "transport")
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "stdio",
                command = stringMember(objectText, "command"),
                args = args,
                enabled = booleanMember(objectText, "enabled") ?: false,
                community = booleanMember(objectText, "community") ?: true,
                url = stringMember(objectText, "url"),
                environment = environment
            )
        }
        require(servers.map { it.name }.toSet().size == servers.size) {
            "mcp.json server names must be unique"
        }
        return servers
    }

    private fun objectValues(arrayText: String): List<String> {
        val values = mutableListOf<String>()
        var index = 1
        while (index < arrayText.length - 1) {
            index = skipWhitespace(arrayText, index)
            if (index >= arrayText.length - 1) break
            require(arrayText[index] != ',') { "mcp.json servers has an unexpected comma" }
            require(arrayText[index] == '{') { "mcp.json servers entries must be objects" }
            val end = matchingEnd(arrayText, index, '{', '}')
            values += arrayText.substring(index, end + 1)
            index = skipWhitespace(arrayText, end + 1)
            if (index < arrayText.length - 1) {
                require(arrayText[index] == ',') { "mcp.json server entries must be comma separated" }
                index++
                val next = skipWhitespace(arrayText, index)
                require(next < arrayText.length - 1) { "mcp.json servers cannot have a trailing comma" }
                index = next
            }
        }
        return values
    }

    private fun stringValues(arrayText: String): List<String> {
        require(arrayText.trimStart().startsWith("[")) { "mcp.json args must be an array" }
        val values = mutableListOf<String>()
        var index = 1
        while (index < arrayText.length - 1) {
            val before = index
            index = skipWhitespace(arrayText, index)
            if (index >= arrayText.length - 1) break
            require(arrayText[index] != ',') { "mcp.json args has an unexpected comma" }
            require(arrayText[index] == '"') { "mcp.json args must contain strings" }
            val end = stringEnd(arrayText, index)
            values += decode(arrayText.substring(index, end + 1))
            index = skipWhitespace(arrayText, end + 1)
            if (index < arrayText.length - 1) {
                require(arrayText[index] == ',') { "mcp.json args entries must be comma separated" }
                index++
                val next = skipWhitespace(arrayText, index)
                require(next < arrayText.length - 1) { "mcp.json args cannot have a trailing comma" }
                index = next
            }
            require(index > before) { "mcp.json args parser made no progress" }
        }
        return values
    }

    private fun stringMap(objectText: String): Map<String, String> {
        val raw = objectText.trim()
        require(raw.startsWith("{") && raw.endsWith("}")) { "mcp.json env must be an object" }
        val members = linkedMapOf<String, String>()
        var index = 1
        while (index < raw.length - 1) {
            index = skipWhitespace(raw, index)
            if (index >= raw.length - 1) break
            require(raw[index] == '"') { "mcp.json env keys must be strings" }
            val keyEnd = stringEnd(raw, index)
            val key = decode(raw.substring(index, keyEnd + 1))
            require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                "mcp.json env key is not a valid environment name: $key"
            }
            require(key !in members) { "mcp.json env keys must be unique: $key" }
            val colon = skipWhitespace(raw, keyEnd + 1)
            require(colon < raw.length && raw[colon] == ':') { "mcp.json env member '$key' is missing ':'" }
            val valueStart = skipWhitespace(raw, colon + 1)
            require(valueStart < raw.length && raw[valueStart] == '"') { "mcp.json env values must be strings" }
            val valueEnd = stringEnd(raw, valueStart)
            val value = decode(raw.substring(valueStart, valueEnd + 1))
            require(value.length <= MAX_ENV_VALUE_CHARS) { "mcp.json env value exceeds $MAX_ENV_VALUE_CHARS characters" }
            members[key] = value
            index = skipWhitespace(raw, valueEnd + 1)
            if (index < raw.length - 1) {
                require(raw[index] == ',') { "mcp.json env members must be comma separated" }
                index++
                val next = skipWhitespace(raw, index)
                require(next < raw.length - 1) { "mcp.json env cannot have a trailing comma" }
                index = next
            }
        }
        require(members.size <= MAX_ENV_ENTRIES) { "mcp.json env has too many entries" }
        return members
    }

    private fun stringMember(text: String, name: String): String? =
        rawMember(text, name)?.let(::decode)

    private fun booleanMember(text: String, name: String): Boolean? {
        val raw = rawMember(text, name)?.trim() ?: return null
        require(raw == "true" || raw == "false") {
            "mcp.json '$name' must be boolean"
        }
        return raw == "true"
    }

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
                        require(colon < text.length && text[colon] == ':') {
                            "mcp.json object member '$key' is missing ':'"
                        }
                        val valueStart = skipWhitespace(text, colon + 1)
                        if (key == name) return rawValue(text, valueStart)
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

    private fun hasTopLevelTrailingComma(text: String): Boolean {
        var quoted = false
        var escaped = false
        var objectDepth = 0
        var arrayDepth = 0
        var lastTopLevel = '\u0000'
        text.forEach { current ->
            if (quoted) {
                if (escaped) escaped = false
                else if (current == '\\') escaped = true
                else if (current == '"') quoted = false
                return@forEach
            }
            when (current) {
                '"' -> quoted = true
                '{' -> objectDepth++
                '}' -> objectDepth--
                '[' -> arrayDepth++
                ']' -> arrayDepth--
                else -> if (objectDepth == 1 && arrayDepth == 0 && !current.isWhitespace()) lastTopLevel = current
            }
        }
        return lastTopLevel == ','
    }

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

    private const val MAX_ENV_ENTRIES = 32
    private const val MAX_ENV_VALUE_CHARS = 8_192
}
