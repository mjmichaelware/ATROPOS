/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

/**
 * A consumer of execution events, named rather than anonymous.
 *
 * Source Doc 3 §5.1 requires `EventPublisher` and `EventSubscriber` as separate
 * atomic files, and the separation earns itself here: a subscriber that is a
 * bare lambda cannot be reported on. When a surface stops updating, the
 * question is which subscriber died, and a `CopyOnWriteArrayList<(E) -> Unit>`
 * answers "one of four".
 *
 * [name] is what makes a failing subscriber nameable in a diagnostic. [interest]
 * is what keeps a phone from rendering events it will never show — filtering at
 * the publisher is cheaper than filtering in every renderer, and a subscriber
 * that declares its interest can be skipped before its lambda is entered.
 */
fun interface EventSubscriber {
    fun onEvent(event: ExecutionEvent)
}

/**
 * A subscriber with an identity and a declared interest.
 *
 * Registered through [ProvenanceStream.subscribe]. The stream holds these
 * rather than raw lambdas so that [ProvenanceStream.subscribers] can report
 * who is listening, and so a subscriber that throws can be named in the
 * warning rather than silently dropped.
 */
data class RegisteredSubscriber(
    val name: String,
    val interest: EventInterest,
    val subscriber: EventSubscriber
) {
    /** True when this subscriber should be handed [event]. */
    fun wants(event: ExecutionEvent): Boolean = interest.matches(event)
}

/**
 * What a subscriber wants to see.
 *
 * All fields null means everything, which is the honest default for a
 * transcript. A surface that only renders one run supplies [runId] and is then
 * never woken by another run's events — which matters on a device where the
 * cost of an unwanted event is a recomposition, not a branch.
 */
data class EventInterest(
    val runId: String? = null,
    val roles: Set<ExecutionRole>? = null,
    val projectId: String? = null,
    val requirement: String? = null
) {
    fun matches(event: ExecutionEvent): Boolean {
        if (runId != null && event.runId != runId) return false
        if (projectId != null && event.projectId != projectId) return false
        if (requirement != null && event.requirement != requirement) return false
        if (roles != null && event.role !in roles) return false
        return true
    }

    companion object {
        /** Everything. */
        val ALL = EventInterest()

        /** One run's events, for a surface pinned to a run. */
        fun ofRun(runId: String) = EventInterest(runId = runId)

        /** One requirement's events, for an evidence drawer. */
        fun ofRequirement(requirement: String) = EventInterest(requirement = requirement)
    }
}
