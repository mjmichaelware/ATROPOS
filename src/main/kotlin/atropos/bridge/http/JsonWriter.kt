/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.http

/**
 * Minimal JSON serialisation.
 *
 * ATROPOS carries no JSON dependency and its durable codecs hand-write their
 * own escaping, so this exists to stop each new wire surface inventing a fourth
 * one. It owns escaping and composition only — never schema, never redaction.
 * Redaction happens before a value reaches here, because a serialiser that also
 * decides what is safe to emit is a serialiser nobody can audit.
 */
object JsonWriter {

    fun str(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    fun num(value: Number): String = value.toString()

    fun bool(value: Boolean): String = value.toString()

    fun nullable(value: String?): String = value?.let(::str) ?: "null"

    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (key, raw) -> "${str(key)}:$raw" }

    fun arr(values: List<String>): String = values.joinToString(",", "[", "]")

    fun strArr(values: List<String>): String = arr(values.map(::str))
}
