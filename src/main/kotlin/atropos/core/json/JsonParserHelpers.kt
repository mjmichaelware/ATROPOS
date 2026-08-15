/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.json

object JsonParserHelpers {
    fun endOfString(json: String, start: Int): Int {
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '\\' -> index += 2 // skip escape and escaped char
                '"' -> return index
                else -> index++
            }
        }
        return -1
    }

    fun skipToValue(json: String, start: Int): Int {
        var index = start
        while (index < json.length) {
            val char = json[index]
            if (char == ':' || char.isWhitespace()) {
                index++
            } else {
                return index
            }
        }
        return -1
    }

    fun openingQuoteAfterKey(json: String, start: Int): Int {
        val colon = json.indexOf(':', start)
        if (colon < 0) return -1
        var index = colon + 1
        while (index < json.length) {
            val char = json[index]
            if (char.isWhitespace()) {
                index++
            } else if (char == '"') {
                return index
            } else {
                return -1
            }
        }
        return -1
    }

    fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        // \uXXXX was falling into the else branch, which drops
                        // the backslash and appends the `u`: `\u0041` came out as
                        // the literal text `u0041` rather than as `A`. Any JSON
                        // carrying a non-ASCII character -- a path with an
                        // accent, a provider message with an em dash -- was
                        // silently corrupted on the way in.
                        val digits = value.substring(index + 2, minOf(index + 6, value.length))
                        val code = digits.takeIf { it.length == 4 }?.toIntOrNull(16)
                        if (code == null) {
                            // Malformed: emitted verbatim, backslash included.
                            // Substituting a replacement character here would
                            // swap text we could not read for text we invented,
                            // and the caller would have no way to tell.
                            out.append('\\').append('u')
                        } else {
                            out.append(code.toChar())
                            index += 4
                        }
                    }
                    else -> out.append(next)
                }
                index += 2
            } else {
                out.append(value[index])
                index++
            }
        }
        return out.toString()
    }

    fun balanced(json: String, open: Int, opener: Char, closer: Char): String? {
        val start = open + 1
        var depth = 1
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '"' -> {
                    val closed = endOfString(json, index + 1)
                    if (closed < 0) return null
                    index = closed + 1
                    continue
                }
                opener -> depth++
                closer -> {
                    depth--
                    if (depth == 0) return json.substring(start, index)
                }
            }
            index++
        }
        return null
    }
}
