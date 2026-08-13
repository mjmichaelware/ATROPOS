/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventJournalRecord

/**
 * Carries provenance through a journal schema that has no columns for it.
 *
 * [EventJournalRecord] predates Source Doc 3 §5.1 and has thirteen correlation
 * ids but no role, requirement, source or state. Widening its schema would
 * invalidate every journal already on disk, and a run history that can no
 * longer be read is a worse failure than the one being fixed — so the extra
 * fields ride in the payload behind a marker instead.
 *
 * The encoding is deliberately dull: a single prefixed line of `key=value`
 * pairs, unit-separated, followed by the original payload. It is greppable, it
 * survives a human opening the journal in a pager, and it degrades to plain
 * text for any reader that does not know about it. A structured format would
 * read better to a parser and worse to the person who actually opens these
 * files at 3am.
 *
 * Decoding is total. A journal line with no marker, a truncated marker, or an
 * unknown role decodes to an event with nulls and [ExecutionRole.SYSTEM] rather
 * than throwing, because a single malformed line must not make the rest of a
 * run's history unreadable.
 */
object ExecutionEventCodec {

    /**
     * Sentinels chosen to survive the journal, not merely to be unlikely.
     *
     * The obvious choice was the unit and record separators, and it failed
     * silently: `EventJournalService` compacts every payload, compaction ends
     * in `trim()`, and Java's `Character.isWhitespace` is true for U+001C
     * through U+001F. The leading separator was stripped before the line
     * reached disk, so every event decoded as legacy and lost its provenance
     * while every write still appeared to succeed.
     *
     * U+0001 and U+0002 are not whitespace under that rule, are not produced
     * by redaction, and cannot occur in a path, a coordinate or a provider id.
     */
    private const val MARKER = "\u0001prov\u0001"
    private const val PAIR = '\u0002'
    private const val ASSIGN = '='

    /** Fields that ride in the payload prefix. Order is stable for greppability. */
    private const val ROLE = "role"
    private const val STATE = "state"
    private const val TASK = "task"
    private const val REQUIREMENT = "requirement"
    private const val SOURCE = "source"
    private const val TERRITORY = "territory"
    private const val EVIDENCE = "evidence"

    /**
     * Builds the payload written to the journal.
     *
     * An event with nothing to add produces its payload unchanged, so a journal
     * only grows a marker where there is provenance worth carrying.
     */
    fun encodePayload(event: ExecutionEvent): String {
        val pairs = buildList {
            add(ROLE to event.role.canonical)
            add(STATE to event.state.name)
            event.task?.let { add(TASK to it) }
            event.requirement?.let { add(REQUIREMENT to it) }
            event.source?.let { add(SOURCE to it) }
            event.territory?.let { add(TERRITORY to it) }
            event.evidenceHash?.let { add(EVIDENCE to it) }
        }
        val header = pairs.joinToString(PAIR.toString()) { (key, value) ->
            key + ASSIGN + sanitize(value)
        }
        return MARKER + header + MARKER + event.payload
    }

    /** Reads a durable record back, recovering provenance when it is present. */
    fun decode(record: EventJournalRecord): ExecutionEvent {
        val raw = record.payload
        if (!raw.startsWith(MARKER)) {
            return bare(record, raw)
        }
        val close = raw.indexOf(MARKER, MARKER.length)
        if (close < 0) {
            // A marker opened and never closed. The line is damaged; read what
            // is legible rather than discarding the event.
            return bare(record, raw.removePrefix(MARKER))
        }
        val fields = parse(raw.substring(MARKER.length, close))
        return ExecutionEvent(
            sequence = record.sequence,
            timestamp = record.timestamp,
            role = ExecutionRole.of(fields[ROLE]),
            category = record.category,
            state = stateOf(fields[STATE]),
            payload = raw.substring(close + MARKER.length),
            provider = record.providerId,
            task = fields[TASK],
            requirement = fields[REQUIREMENT],
            source = fields[SOURCE],
            runId = record.runId,
            goalId = record.goalId,
            projectId = record.projectId,
            dagId = record.dagId,
            atomId = record.atomId,
            jobId = record.jobId,
            attemptId = record.attemptId,
            parentRunId = record.parentRunId,
            territory = fields[TERRITORY],
            evidenceHash = fields[EVIDENCE]
        )
    }

    /**
     * An event from a record written before provenance existed.
     *
     * Its four §5.1 fields stay null, which is the truthful answer.
     * [ExecutionEvent.provenanceComplete] then reports it as incomplete, and
     * the trace-completeness metric counts it as the gap it is instead of
     * being flattered by invented values.
     */
    private fun bare(record: EventJournalRecord, payload: String): ExecutionEvent =
        ExecutionEvent(
            sequence = record.sequence,
            timestamp = record.timestamp,
            role = ExecutionRole.SYSTEM,
            category = record.category,
            state = RunState.IDLE,
            payload = payload,
            provider = record.providerId,
            runId = record.runId,
            goalId = record.goalId,
            projectId = record.projectId,
            dagId = record.dagId,
            atomId = record.atomId,
            jobId = record.jobId,
            attemptId = record.attemptId,
            parentRunId = record.parentRunId
        )

    private fun parse(header: String): Map<String, String> {
        if (header.isEmpty()) return emptyMap()
        val fields = LinkedHashMap<String, String>()
        header.split(PAIR).forEach { pair ->
            val split = pair.indexOf(ASSIGN)
            if (split > 0) {
                val key = pair.substring(0, split)
                val value = restore(pair.substring(split + 1))
                if (value.isNotEmpty()) fields[key] = value
            }
        }
        return fields
    }

    private fun stateOf(value: String?): RunState =
        value?.let { name -> runCatching { RunState.valueOf(name) }.getOrNull() } ?: RunState.IDLE

    /**
     * Removes the two separators from a value.
     *
     * A requirement or a source containing a unit separator would otherwise
     * split its own field and shift every field after it. Replacing rather than
     * escaping keeps decoding a split instead of a state machine, and neither
     * character can occur in a path, a coordinate or a provider id.
     */
    private fun sanitize(value: String): String =
        value.replace(PAIR, ' ').replace('\u0001', ' ').trim()

    private fun restore(value: String): String = value.trim()
}
