/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.json

/**
 * Reads quoted string fields out of JSON text without regex.
 *
 * What this replaces was `Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")`,
 * repeated in nine files. The shape is the problem: `(?:\\.|[^"\\])*` is a
 * *group* repetition containing an alternation, and `java.util.regex` compiles
 * that to a `Loop` node whose `match()` recurses once per iteration — which is
 * once per character of the string being read. A field holding a few thousand
 * characters therefore costs a few thousand stack frames, and the JVM throws
 * `StackOverflowError` rather than returning a result.
 *
 * That is not a hypothetical. It took out both memory channels on a live run:
 *
 * ```
 * st_memory=SKIPPED_SOFT_FAIL:stackoverflowerror
 * lt_memory=SKIPPED_SOFT_FAIL:stackoverflowerror
 * ```
 *
 * and it sat on the provider-response path too, where a long completion is the
 * normal case rather than the exceptional one. Both failures surfaced as
 * soft-fails, so a run with no memory recall at all still reported success.
 *
 * A backslash-aware forward scan is linear in time, constant in stack, and
 * total: it cannot throw. It is also the more honest tool — finding the end of
 * a JSON string is a scan, and expressing a scan as a backtracking pattern was
 * what introduced the recursion in the first place.
 *
 * Behaviour is deliberately identical to the regex it replaces, including its
 * limits: the first syntactically plausible `"name" : "..."` anywhere in the
 * text wins, whether or not it is a top-level key, and a string left open by a
 * trailing backslash is treated as absent rather than as reaching the end.
 * Matching those exactly is what makes this a fix and not a change.
 */
object JsonStringField {

    /**
     * The raw, still-escaped contents of `"name": "..."`, or null.
     *
     * Escapes are left in place because callers own their own unescaping — the
     * codecs and adapters that use this each decode into different targets, and
     * decoding here would force one of them to re-encode.
     */
    fun value(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val opened = openingQuoteAfterKey(json, at + key.length)
            if (opened >= 0) {
                val closed = endOfString(json, opened + 1)
                if (closed >= 0) return json.substring(opened + 1, closed)
            }
            // A name can appear inside a value before it appears as a key, so a
            // near-miss advances rather than concluding the field is absent.
            from = at + 1
        }
    }

    /**
     * Every quoted string in [json], still escaped, in order.
     *
     * Used for arrays of plain strings, where the elements are the only quoted
     * runs present. Scanning rather than splitting on commas keeps a comma
     * inside an element from ending it.
     */
    fun values(json: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < json.length) {
            if (json[index] == '"') {
                val closed = endOfString(json, index + 1)
                if (closed < 0) break
                found += json.substring(index + 1, closed)
                index = closed + 1
            } else {
                index++
            }
        }
        return found
    }

    /**
     * The bracketed body of `"name": [ ... ]`, or null.
     *
     * Bracket depth is tracked outside strings only, so a `]` inside an element
     * does not close the array — which the lazy `\[(.*?)\]` this replaces got
     * wrong.
     */
    fun arrayBody(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = skipToValue(json, at + key.length)
            if (index >= 0 && index < json.length && json[index] == '[') {
                return balanced(json, index, '[', ']')
            }
            from = at + 1
        }
    }

    /**
     * The braced body of `"name": { ... }`, or null.
     *
     * The object counterpart of [arrayBody], and depth-tracked outside strings
     * for the same reason: a `}` inside a value must not close the object. Used
     * for reaching one level into a document — `"project"`, `"execution"` — so a
     * caller can then read fields from that scope rather than from the whole
     * file, where a name may appear more than once.
     */
    fun objectBody(json: String, name: String): String? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = skipToValue(json, at + key.length)
            if (index >= 0 && index < json.length && json[index] == '{') {
                return balanced(json, index, '{', '}')
            }
            from = at + 1
        }
    }

    /**
     * The top-level object bodies inside an array body, in order.
     *
     * For `[{...},{...}]` this yields each element's contents without its outer
     * braces. Nested objects are stepped over rather than returned, so an
     * element carrying its own sub-object still arrives as one element.
     *
     * Splitting on `},{` would be the obvious shortcut and is wrong on exactly
     * the inputs that matter: a nested object, or a brace inside a quoted
     * statement, both split an element in half. Depth tracking costs the same
     * scan and cannot.
     */
    fun objectsIn(arrayBody: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (index < arrayBody.length) {
            when (arrayBody[index]) {
                '"' -> {
                    val closed = endOfString(arrayBody, index + 1)
                    if (closed < 0) return found
                    index = closed + 1
                }
                '{' -> {
                    val body = balanced(arrayBody, index, '{', '}') ?: return found
                    found += body
                    // +2 for the braces the body excludes.
                    index += body.length + 2
                }
                else -> index++
            }
        }
        return found
    }

    /**
     * The contents between a bracket at [open] and its match, exclusive.
     *
     * Shared by [arrayBody], [objectBody] and [objectsIn]. Quoted runs are
     * skipped whole, which is what keeps a bracket inside a string from
     * changing the depth.
     */
    private fun balanced(json: String, open: Int, opener: Char, closer: Char): String? {
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

    /**
     * The unescaped value of `"name": "..."`, or null.
     *
     * [value] deliberately leaves escapes in place for callers that re-encode.
     * Callers that want the text — a requirement statement, a node title —
     * would each have to write the same six-case decoder, so it lives here once.
     */
    fun text(json: String, name: String): String? = value(json, name)?.let(::unescape)

    /**
     * A JSON string literal's escapes resolved.
     *
     * `\uXXXX` is decoded; a malformed one is left as written rather than
     * throwing, because a single bad escape in one field should not fail the
     * parse of an otherwise readable document.
     */
    fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val out = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val current = raw[index]
            if (current != '\\' || index + 1 >= raw.length) {
                out.append(current)
                index++
                continue
            }
            when (val escape = raw[index + 1]) {
                '"', '\\', '/' -> { out.append(escape); index += 2 }
                'n' -> { out.append('\n'); index += 2 }
                't' -> { out.append('\t'); index += 2 }
                'r' -> { out.append('\r'); index += 2 }
                'b' -> { out.append('\b'); index += 2 }
                'f' -> { out.append('\u000C'); index += 2 }
                'u' -> {
                    val hex = raw.substring(index + 2, minOf(index + 6, raw.length))
                    val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                    if (code == null) {
                        out.append(current)
                        index++
                    } else {
                        out.append(code.toChar())
                        index += 6
                    }
                }
                else -> { out.append(current); index++ }
            }
        }
        return out.toString()
    }

    /**
     * The numeric value of `"name": 123`, or null.
     *
     * Unquoted, so [value] cannot read it. Returns null rather than 0 for an
     * absent field: a byte count that is missing and a byte count that is zero
     * are different facts, and a verifier must not confuse them.
     */
    fun longValue(json: String, name: String): Long? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = skipToValue(json, at + key.length)
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

    /** The boolean value of `"name": true`, or null when absent or non-boolean. */
    fun booleanValue(json: String, name: String): Boolean? {
        val key = "\"" + name + "\""
        var from = 0
        while (true) {
            val at = json.indexOf(key, from)
            if (at < 0) return null
            val index = skipToValue(json, at + key.length)
            if (index in 0 until json.length) {
                if (json.startsWith("true", index)) return true
                if (json.startsWith("false", index)) return false
            }
            from = at + 1
        }
    }

    /** Index of the `"` opening this key's string value, or -1. */
    private fun openingQuoteAfterKey(json: String, afterKey: Int): Int {
        val index = skipToValue(json, afterKey)
        if (index < 0 || index >= json.length) return -1
        return if (json[index] == '"') index else -1
    }

    /** Index of the value after `<ws> : <ws>`, or -1 when the colon is absent. */
    private fun skipToValue(json: String, afterKey: Int): Int {
        var index = afterKey
        while (index < json.length && json[index].isWhitespace()) index++
        if (index >= json.length || json[index] != ':') return -1
        index++
        while (index < json.length && json[index].isWhitespace()) index++
        return index
    }

    /**
     * Index of the `"` closing a string that starts at [start], or -1.
     *
     * A backslash consumes the character after it whatever that character is,
     * which is what makes `\"` stay inside the string and `\\` not escape the
     * quote that follows it.
     */
    private fun endOfString(json: String, start: Int): Int {
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '\\' -> index += 2
                '"' -> return index
                else -> index++
            }
        }
        return -1
    }
}
