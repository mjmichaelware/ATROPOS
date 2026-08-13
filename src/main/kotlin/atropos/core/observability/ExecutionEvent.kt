/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalRecord
import java.time.Instant

/**
 * One visible event, carrying the provenance Source Doc 3 §5.1 requires.
 *
 * > Every visible event carries timestamp, role, provider, task, requirement,
 * > source, and state.
 *
 * [EventJournalRecord] is the storage owner and already carries thirteen
 * correlation ids, but four of those seven fields are not among them: role,
 * requirement, source and state. This type is the missing half, not a second
 * event system — the journal remains the only thing that writes to disk, and
 * every [ExecutionEvent] either came from a journal record or is on its way to
 * becoming one.
 *
 * The distinction matters because the non-duplication law forbids a second
 * journal, and because a run's durable history and its live stream disagreeing
 * about what happened would make both useless. One record, two readings.
 *
 * @param requirement the source coordinate this event serves, in the live
 *   `document#section@Lstart-end` form. Null when the event answers to no
 *   requirement — a heartbeat, a GC pass — which is different from an event
 *   whose requirement was never recorded, and the two must not be conflated.
 * @param source where the event came from: a file path, a provider id, a
 *   command. Distinct from [requirement], which is the authority the work
 *   answers to.
 */
data class ExecutionEvent(
    val sequence: Long,
    val timestamp: Instant,
    val role: ExecutionRole,
    val category: EventCategory,
    val state: RunState,
    val payload: String,
    val provider: String? = null,
    val task: String? = null,
    val requirement: String? = null,
    val source: String? = null,
    val runId: String? = null,
    val goalId: String? = null,
    val projectId: String? = null,
    val dagId: String? = null,
    val atomId: String? = null,
    val jobId: String? = null,
    val attemptId: String? = null,
    val parentRunId: String? = null,
    val territory: String? = null,
    val evidenceHash: String? = null
) {
    /**
     * True when every field §5.1 names is present.
     *
     * Trace completeness is a Source Doc 3 §4.1 metric, and a metric needs a
     * predicate rather than an impression. This is that predicate, defined on
     * the event itself so the metric cannot drift from the requirement it
     * measures.
     */
    val provenanceComplete: Boolean
        get() = provider != null && task != null && requirement != null && source != null

    /** The §5.1 fields that are absent, for a report that says what is missing. */
    fun missingProvenance(): List<String> = buildList {
        if (provider == null) add("provider")
        if (task == null) add("task")
        if (requirement == null) add("requirement")
        if (source == null) add("source")
    }

    /**
     * A one-line rendering, provenance first.
     *
     * Ordered so the eye reaches role and state before payload: an operator
     * scanning a wall of events is looking for who and what-now, and the
     * payload is what they read once they have found the line.
     */
    fun render(): String = buildString {
        append('#').append(sequence).append(' ')
        append(timestamp.toString().substringAfter('T').substringBefore('.'))
        append(" [").append(role.canonical).append('/').append(state.label).append(']')
        append(" (").append(category.name).append(')')
        provider?.let { append(" provider=").append(it) }
        task?.let { append(" task=").append(it) }
        requirement?.let { append(" requirement=").append(it) }
        source?.let { append(" source=").append(it) }
        territory?.let { append(" territory=").append(it) }
        evidenceHash?.let { append(" evidence=").append(it.take(16)) }
        append(": ").append(payload.take(PAYLOAD_PREVIEW))
    }

    /**
     * Converts back to the durable record.
     *
     * The four provenance fields have no column in [EventJournalRecord], so
     * they travel in the payload as a prefix that [ExecutionEventCodec] can
     * read back. Widening the journal schema instead would break every
     * already-written journal on disk, and a history that cannot be read is a
     * worse outcome than a payload with a header on it.
     */
    fun toJournalRecord(): EventJournalRecord = EventJournalRecord(
        sequence = sequence,
        timestamp = timestamp,
        category = category,
        payload = ExecutionEventCodec.encodePayload(this),
        goalId = goalId,
        projectId = projectId,
        dagId = dagId,
        atomId = atomId,
        jobId = jobId,
        attemptId = attemptId,
        runId = runId,
        parentRunId = parentRunId,
        providerId = provider
    )

    companion object {
        const val PAYLOAD_PREVIEW = 200

        /**
         * Reads a durable record back as an event.
         *
         * Provenance written by [toJournalRecord] is recovered; a record
         * written before this type existed yields an event with nulls, which
         * [provenanceComplete] then reports honestly rather than inventing
         * values to make an old record look compliant.
         */
        fun fromJournalRecord(record: EventJournalRecord): ExecutionEvent =
            ExecutionEventCodec.decode(record)
    }
}
