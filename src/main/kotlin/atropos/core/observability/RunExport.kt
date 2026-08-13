/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import java.time.Instant

/**
 * Everything one run produced, assembled once for any exporter to read.
 *
 * Source Doc 3 §5.2 requires a full run to be exportable "as Markdown, JSON, or
 * another durable trace format", and §4.3 requires one small file per format.
 * That only stays true if the formats share an assembled shape — otherwise each
 * exporter walks the journal itself, and three walks of the same journal
 * produce three subtly different exports of the same run.
 *
 * This is that shape. It is deliberately inert: no I/O, no formatting, no
 * filtering beyond what the caller asked for. [MarkdownExporter] and
 * [JsonExporter] are pure functions over it, which is what makes an
 * export reproducible — the same run exported twice is byte-identical, and
 * Source Doc 3 §4.1 counts event determinism as a metric.
 */
data class RunExport(
    val runId: String,
    val exportedAt: Instant,
    val events: List<ExecutionEvent>,
    val cards: List<OutputCard>
) {
    val eventCount: Int get() = events.size
    val cardCount: Int get() = cards.size

    /** First and last event timestamps, or null for an empty run. */
    val span: Pair<Instant, Instant>?
        get() = events.firstOrNull()?.timestamp?.let { first ->
            first to events.last().timestamp
        }

    /**
     * The share of events carrying all seven §5.1 fields.
     *
     * This is the trace-completeness metric Source Doc 3 §4.1 names, computed
     * where the data is rather than in a separate metrics pass that would have
     * to re-walk the journal. Returns 1.0 for an empty run: nothing was
     * recorded incompletely, which is different from nothing being recorded and
     * is why [eventCount] travels alongside it.
     */
    val traceCompleteness: Double
        get() = if (events.isEmpty()) 1.0
        else events.count { it.provenanceComplete }.toDouble() / events.size

    /** Events missing at least one required field, for a report that can name them. */
    fun incompleteEvents(): List<ExecutionEvent> = events.filterNot { it.provenanceComplete }

    /** Distinct requirements this run touched, in first-seen order. */
    fun requirements(): List<String> =
        events.mapNotNull { it.requirement }.distinct()

    /** Distinct providers this run called, in first-seen order. */
    fun providers(): List<String> =
        events.mapNotNull { it.provider }.distinct()

    /** Roles that acted in this run, in hierarchy order. */
    fun roles(): List<ExecutionRole> =
        events.map { it.role }.distinct().sortedBy { it.level }

    /** Cards that record a failure — the ones a reader opens an export for. */
    fun failures(): List<OutputCard> = cards.filter { it.failed }

    companion object {
        /**
         * Assembles from a run's events.
         *
         * Cards are derived here rather than stored, so a card and the event it
         * came from can never disagree — there is only one record, read twice.
         */
        fun of(runId: String, events: List<ExecutionEvent>, at: Instant = Instant.now()): RunExport =
            RunExport(
                runId = runId,
                exportedAt = at,
                events = events,
                cards = events.mapNotNull(OutputCard::from)
            )
    }
}
