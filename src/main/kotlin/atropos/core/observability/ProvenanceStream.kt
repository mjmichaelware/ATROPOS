/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The live execution stream, as distinct from the reasoning stream.
 *
 * `ThinkingStream` carries what the engine is *thinking* at a depth an operator
 * chose. This carries what the engine is *doing*, with the provenance Source
 * Doc 3 §5.1 requires on every visible event. They are deliberately separate:
 * a thought has a depth and no provenance, an execution event has provenance
 * and no depth, and collapsing them would force every producer to supply both.
 *
 * Bounded for the same reason `ThinkingStream` is — a long autonomous run emits
 * thousands of events and a phone has neither the memory to retain them nor a
 * scrollback worth retaining them for. The bound is the *live tail*; the
 * journal on disk is the history, and `ExecutionHistoryStore` is how it is
 * queried. Nothing here is the record of anything.
 *
 * Subscribers are named and declare an interest, so a stalled surface can be
 * identified and an uninterested one is never entered. See [EventSubscriber].
 */
class ProvenanceStream(private val bound: Int = DEFAULT_BOUND) {

    private val retained = ArrayDeque<ExecutionEvent>()
    private val registered = CopyOnWriteArrayList<RegisteredSubscriber>()
    private val failures = CopyOnWriteArrayList<SubscriberFailure>()

    /**
     * Publishes an event to every interested subscriber.
     *
     * A subscriber that throws is recorded and skipped, never propagated. A
     * renderer failing to draw is cosmetic; the same exception reaching the
     * engine converts it into a lost run, which is the outcome the whole
     * observability layer exists to prevent.
     */
    fun emit(event: ExecutionEvent) {
        synchronized(retained) {
            retained.addLast(event)
            while (retained.size > bound) retained.removeFirst()
        }
        registered.forEach { entry ->
            if (!entry.wants(event)) return@forEach
            runCatching { entry.subscriber.onEvent(event) }.onFailure { failure ->
                record(entry.name, event.sequence, failure)
            }
        }
    }

    /**
     * Registers a named subscriber.
     *
     * @return a handle that removes the subscription. Held by the caller rather
     *   than keyed by name, so two surfaces may register the same name without
     *   one unsubscribing the other.
     */
    fun subscribe(
        name: String,
        interest: EventInterest = EventInterest.ALL,
        subscriber: EventSubscriber
    ): () -> Unit {
        val entry = RegisteredSubscriber(name, interest, subscriber)
        registered += entry
        return { registered.remove(entry) }
    }

    /** Who is listening, for a diagnostic that can name a stalled surface. */
    fun subscribers(): List<String> = registered.map { it.name }

    /** The retained tail, oldest first. */
    fun replay(): List<ExecutionEvent> = synchronized(retained) { retained.toList() }

    /** The retained tail matching [interest]. */
    fun replay(interest: EventInterest): List<ExecutionEvent> =
        replay().filter(interest::matches)

    /**
     * Subscriber failures since the last [clear].
     *
     * Kept rather than logged and forgotten: a surface that silently stopped
     * receiving events looks identical to a run that stopped producing them,
     * and only this list distinguishes the two.
     */
    fun subscriberFailures(): List<SubscriberFailure> = failures.toList()

    fun clear() {
        synchronized(retained) { retained.clear() }
        failures.clear()
    }

    private fun record(name: String, sequence: Long, failure: Throwable) {
        failures += SubscriberFailure(
            subscriber = name,
            sequence = sequence,
            reason = failure::class.simpleName.orEmpty() + ": " + (failure.message ?: "no message")
        )
        while (failures.size > FAILURE_BOUND) failures.removeAt(0)
    }

    /** One subscriber's refusal of one event. */
    data class SubscriberFailure(
        val subscriber: String,
        val sequence: Long,
        val reason: String
    )

    companion object {
        const val DEFAULT_BOUND = 2_000
        const val FAILURE_BOUND = 100

        /**
         * The process-wide stream.
         *
         * One instance for the same reason `Thinking.stream` is one: a producer
         * emitting into a second stream is a producer nothing renders, and the
         * failure mode is silence rather than an error.
         */
        val instance: ProvenanceStream = ProvenanceStream()
    }
}
