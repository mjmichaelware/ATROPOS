/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

/**
 * A run as JSON, for a machine to read.
 *
 * The sibling of [MarkdownExporter] and deliberately not a reformatting of it:
 * Markdown orders for a person opening an export cold, JSON orders for a
 * consumer that will index it. Every field appears explicitly, including nulls,
 * because a consumer that has to distinguish "absent" from "not recorded"
 * cannot do so if absent fields are simply omitted — and that distinction is
 * exactly what the trace-completeness metric turns on.
 *
 * Hand-written rather than reflected. The engine has no serialization
 * dependency (Source Doc 2 rule 135: avoid serialization libraries unless
 * already present), and a hand-written writer makes the wire shape a decision
 * in a file rather than a consequence of field order in a data class.
 *
 * Escaping is exhaustive over the characters JSON forbids raw, including the
 * control characters the provenance codec uses as sentinels — those reach a
 * payload only when something has gone wrong, and an export that emits invalid
 * JSON at precisely that moment is an export that fails when it is needed.
 */
class JsonExporter(private val indent: String = "  ") {

    fun export(run: RunExport): String = buildString {
        appendLine("{")
        field(1, "runId", run.runId, comma = true)
        field(1, "exportedAt", run.exportedAt.toString(), comma = true)
        numberField(1, "eventCount", run.eventCount, comma = true)
        numberField(1, "cardCount", run.cardCount, comma = true)
        rawField(1, "traceCompleteness", formatRatio(run.traceCompleteness), comma = true)
        arrayField(1, "requirements", run.requirements(), comma = true)
        arrayField(1, "providers", run.providers(), comma = true)
        arrayField(1, "roles", run.roles().map { it.canonical }, comma = true)
        appendEvents(run)
        appendCards(run)
        appendLine("}")
    }

    private fun StringBuilder.appendEvents(run: RunExport) {
        append(indent).appendLine("\"events\": [")
        run.events.forEachIndexed { index, event ->
            append(indent.repeat(2)).appendLine("{")
            numberField(3, "sequence", event.sequence, comma = true)
            field(3, "timestamp", event.timestamp.toString(), comma = true)
            field(3, "role", event.role.canonical, comma = true)
            field(3, "state", event.state.label, comma = true)
            field(3, "category", event.category.name, comma = true)
            nullableField(3, "provider", event.provider, comma = true)
            nullableField(3, "task", event.task, comma = true)
            nullableField(3, "requirement", event.requirement, comma = true)
            nullableField(3, "source", event.source, comma = true)
            nullableField(3, "runId", event.runId, comma = true)
            nullableField(3, "goalId", event.goalId, comma = true)
            nullableField(3, "projectId", event.projectId, comma = true)
            nullableField(3, "dagId", event.dagId, comma = true)
            nullableField(3, "atomId", event.atomId, comma = true)
            nullableField(3, "jobId", event.jobId, comma = true)
            nullableField(3, "territory", event.territory, comma = true)
            nullableField(3, "evidenceHash", event.evidenceHash, comma = true)
            rawField(3, "provenanceComplete", event.provenanceComplete.toString(), comma = true)
            arrayField(3, "missingProvenance", event.missingProvenance(), comma = true)
            field(3, "payload", event.payload, comma = false)
            append(indent.repeat(2)).appendLine(if (index == run.events.lastIndex) "}" else "},")
        }
        append(indent).appendLine("],")
    }

    private fun StringBuilder.appendCards(run: RunExport) {
        append(indent).appendLine("\"cards\": [")
        run.cards.forEachIndexed { index, card ->
            append(indent.repeat(2)).appendLine("{")
            numberField(3, "sequence", card.sequence, comma = true)
            field(3, "kind", card.kind.name, comma = true)
            field(3, "title", card.title, comma = true)
            nullableField(3, "language", card.language, comma = true)
            nullableField(3, "requirement", card.requirement, comma = true)
            nullableField(3, "provider", card.provider, comma = true)
            field(3, "role", card.role.canonical, comma = true)
            nullableField(3, "evidenceHash", card.evidenceHash, comma = true)
            rawField(3, "failed", card.failed.toString(), comma = true)
            numberField(3, "copyBytes", card.copyBytes(), comma = true)
            field(3, "body", card.body, comma = false)
            append(indent.repeat(2)).appendLine(if (index == run.cards.lastIndex) "}" else "},")
        }
        append(indent).appendLine("]")
    }

    private fun StringBuilder.field(depth: Int, name: String, value: String, comma: Boolean) {
        append(indent.repeat(depth)).append('"').append(name).append("\": ")
        append('"').append(escape(value)).append('"')
        appendLine(if (comma) "," else "")
    }

    /**
     * Writes null explicitly rather than omitting the key.
     *
     * A consumer distinguishing "this run had no provider" from "this exporter
     * does not emit providers" cannot do it from an absent key, and that is the
     * distinction the completeness metric is built on.
     */
    private fun StringBuilder.nullableField(depth: Int, name: String, value: String?, comma: Boolean) {
        if (value == null) rawField(depth, name, "null", comma) else field(depth, name, value, comma)
    }

    private fun StringBuilder.numberField(depth: Int, name: String, value: Number, comma: Boolean) {
        rawField(depth, name, value.toString(), comma)
    }

    private fun StringBuilder.rawField(depth: Int, name: String, literal: String, comma: Boolean) {
        append(indent.repeat(depth)).append('"').append(name).append("\": ").append(literal)
        appendLine(if (comma) "," else "")
    }

    private fun StringBuilder.arrayField(depth: Int, name: String, values: List<String>, comma: Boolean) {
        append(indent.repeat(depth)).append('"').append(name).append("\": [")
        append(values.joinToString(", ") { "\"" + escape(it) + "\"" })
        append(']')
        appendLine(if (comma) "," else "")
    }

    /** Fixed to three places so the same run exports byte-identically twice. */
    private fun formatRatio(value: Double): String = String.format("%.3f", value)

    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { ch ->
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch == '\b' -> append("\\b")
                ch == '' -> append("\\f")
                ch < ' ' -> append("\\u").append(String.format("%04x", ch.code))
                else -> append(ch)
            }
        }
    }
}
