/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * The single place an execution event enters the system.
 *
 * Publishing is two writes that must not diverge: the durable one to
 * [EventJournalService], and the live one to [ProvenanceStream]. A producer
 * doing both by hand will eventually do one, and then the transcript and the
 * history disagree about what happened — which is worse than either being
 * absent, because both look authoritative.
 *
 * Order is deliberate. The journal is written first and the stream second, so
 * an event a surface displayed is always an event that survived a restart. The
 * reverse order can show an operator something that is then lost, and an
 * operator who has seen a lost event has no way to tell that is what happened.
 *
 * Redaction runs here, once, before either write. Source Doc 2 rule 9:
 * "redaction runs before logging, status, persistence, and model prompts".
 * Doing it at the publisher rather than in each producer is what makes that
 * rule mechanical instead of remembered.
 */
class EventPublisher(
    private val journal: EventJournalService = EventJournalService(),
    private val stream: ProvenanceStream = ProvenanceStream.instance,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val clock: () -> Instant = { Instant.now() }
) {

    /**
     * Publishes one event.
     *
     * @param runId the run this belongs to. Required, because an event that
     *   belongs to no run cannot be found again — the journal is partitioned by
     *   run and there is no directory for events without one.
     * @return the event as published, with its assigned sequence and redaction
     *   applied, so a caller that needs to reference what it just wrote does
     *   not have to guess at either.
     */
    fun publish(
        runId: String,
        role: ExecutionRole,
        category: EventCategory,
        state: RunState,
        payload: String,
        provider: String? = null,
        task: String? = null,
        requirement: String? = null,
        source: String? = null,
        goalId: String? = null,
        projectId: String? = null,
        dagId: String? = null,
        atomId: String? = null,
        jobId: String? = null,
        attemptId: String? = null,
        parentRunId: String? = null,
        territory: String? = null,
        evidenceHash: String? = null
    ): ExecutionEvent {
        val safePayload = redactionFilter.redact(payload)
        val record = journal.record(
            runId = runId,
            category = category,
            payload = ExecutionEventCodec.encodePayload(
                draft(
                    role = role,
                    category = category,
                    state = state,
                    payload = safePayload,
                    task = task,
                    requirement = requirement,
                    source = source,
                    territory = territory,
                    evidenceHash = evidenceHash
                )
            ),
            goalId = goalId,
            projectId = projectId,
            dagId = dagId,
            atomId = atomId,
            jobId = jobId,
            attemptId = attemptId,
            parentRunId = parentRunId,
            providerId = provider
        )
        val event = ExecutionEvent(
            sequence = record.sequence,
            timestamp = record.timestamp,
            role = role,
            category = category,
            state = state,
            payload = safePayload,
            provider = provider,
            task = task?.let(redactionFilter::redact),
            requirement = requirement,
            source = source?.let(redactionFilter::redact),
            runId = runId,
            goalId = goalId,
            projectId = projectId,
            dagId = dagId,
            atomId = atomId,
            jobId = jobId,
            attemptId = attemptId,
            parentRunId = parentRunId,
            territory = territory,
            evidenceHash = evidenceHash
        )
        stream.emit(event)
        return event
    }

    /**
     * Publishes an event that answers to a source requirement.
     *
     * Named separately because the requirement is the field most often omitted
     * and least often noticed: an event without one still renders, still
     * persists, and quietly fails trace completeness. A call site that has the
     * coordinate should not have to remember which of eighteen parameters
     * carries it.
     */
    fun publishForRequirement(
        runId: String,
        requirement: String,
        role: ExecutionRole,
        category: EventCategory,
        state: RunState,
        payload: String,
        task: String,
        source: String,
        provider: String? = null,
        territory: String? = null,
        evidenceHash: String? = null
    ): ExecutionEvent = publish(
        runId = runId,
        role = role,
        category = category,
        state = state,
        payload = payload,
        provider = provider,
        task = task,
        requirement = requirement,
        source = source,
        territory = territory,
        evidenceHash = evidenceHash
    )

    /** The provenance-bearing shape used to build the journal payload prefix. */
    private fun draft(
        role: ExecutionRole,
        category: EventCategory,
        state: RunState,
        payload: String,
        task: String?,
        requirement: String?,
        source: String?,
        territory: String?,
        evidenceHash: String?
    ) = ExecutionEvent(
        sequence = 0,
        timestamp = clock(),
        role = role,
        category = category,
        state = state,
        payload = payload,
        task = task?.let(redactionFilter::redact),
        requirement = requirement,
        source = source?.let(redactionFilter::redact),
        territory = territory,
        evidenceHash = evidenceHash
    )
}
