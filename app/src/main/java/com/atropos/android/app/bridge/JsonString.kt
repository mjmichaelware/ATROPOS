/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

/**
 * Escaping for the one JSON string this app ever writes.
 *
 * A message typed on a phone routinely contains quotes, newlines and emoji.
 * Concatenating it into a body unescaped produces malformed JSON, which the
 * bridge rejects as a bad request — indistinguishable, from the operator's
 * side, from the engine refusing what they said.
 */
object JsonString {
    fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '' -> append("\\f")
                else ->
                    // Control characters are not legal raw in a JSON string.
                    if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
