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
            var index = skipToValue(json, at + key.length)
            if (index >= 0 && index < json.length && json[index] == '[') {
                val start = index + 1
                var depth = 1
                index = start
                while (index < json.length) {
                    when (json[index]) {
                        '"' -> {
                            val closed = endOfString(json, index + 1)
                            if (closed < 0) return null
                            index = closed + 1
                            continue
                        }
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) return json.substring(start, index)
                        }
                    }
                    index++
                }
                return null
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
