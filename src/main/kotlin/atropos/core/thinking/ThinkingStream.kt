/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Reasoning as it happens, rather than after it is over.
 *
 * [ThinkingRecord] models reasoning that has already been produced, and the
 * bridge serves it over `/v1/thinking`. Neither helps an operator watching a
 * terminal: a run that takes fourteen minutes and prints one spinner line is
 * indistinguishable from a run that has hung, and the only way to find out
 * which is to kill it — which destroys the thing you were trying to inspect.
 *
 * This is the live half. Producers append; surfaces subscribe and render at
 * whatever depth they are set to. The two ends know nothing about each other,
 * which is what lets engine code narrate without importing a renderer.
 *
 * Bounded, because a long autonomous run emits thousands of lines and a phone
 * has neither the memory to hold them nor a scrollback worth holding them for.
 * The tail is what an operator reads; the whole trace is what the evidence
 * bundle is for.
 */
class ThinkingStream(private val bound: Int = DEFAULT_BOUND) {

    private val lines = ArrayDeque<StreamedThought>()
    private val subscribers = CopyOnWriteArrayList<(StreamedThought) -> Unit>()

    /**
     * Publishes a thought.
     *
     * A subscriber that throws must not take the run down with it. A renderer
     * failing to draw is a cosmetic problem; the same failure propagating into
     * the engine would turn it into a lost run, which is precisely the outcome
     * this whole file exists to prevent.
     */
    fun emit(depth: ThinkingDepth, text: String, at: Instant = Instant.now()) {
        if (text.isBlank()) return
        val thought = StreamedThought(depth, text.trim(), at)
        synchronized(lines) {
            lines.addLast(thought)
            while (lines.size > bound) lines.removeFirst()
        }
        subscribers.forEach { subscriber ->
            runCatching { subscriber(thought) }
        }
    }

    /** @return a handle that removes the subscription. */
    fun subscribe(subscriber: (StreamedThought) -> Unit): () -> Unit {
        subscribers += subscriber
        return { subscribers.remove(subscriber) }
    }

    /** Everything retained, at or below [depth]. */
    fun replay(depth: ThinkingDepth): List<StreamedThought> =
        synchronized(lines) { lines.toList() }.filter { depth.includes(it.depth) }

    fun clear() {
        synchronized(lines) { lines.clear() }
    }

    private companion object {
        /** Enough to explain a long run; small enough to hold on a phone. */
        const val DEFAULT_BOUND = 2_000
    }
}

data class StreamedThought(
    val depth: ThinkingDepth,
    val text: String,
    val at: Instant
)

/**
 * The one stream a process has.
 *
 * Engine code narrates without being handed a stream, and the CLI subscribes
 * without being handed one either. Threading it through every constructor
 * between the two would mean every intermediate layer knew about reasoning
 * output, and the first layer that forgot to pass it along would silently
 * disconnect everything beneath it.
 */
object Thinking {
    val stream: ThinkingStream = ThinkingStream()
}
