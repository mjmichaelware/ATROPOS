/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter
import atropos.core.thinking.StreamedThought
import atropos.core.thinking.Thinking
import atropos.core.thinking.ThinkingChannels
import atropos.core.thinking.ThinkingDepth
import atropos.core.thinking.ThinkingStream

/**
 * Shows a long run working, at the depth the operator asked for.
 *
 * What this replaces is a spinner that said "Running Phase 11 self-host loop"
 * and then nothing for fourteen minutes. A spinner communicates exactly one
 * bit — *something is happening* — and that bit stops being informative within
 * about ten seconds. After that the operator cannot distinguish progress from
 * a hang, and the only available action destroys the run.
 *
 * Depth comes from [ThinkingChannels] under the `cli` surface, so a terminal
 * expanded to L3 does not drag the bridge or the phone with it. `HOE-E04`
 * requires that isolation and this is the CLI's end of it.
 *
 * Every line is redacted. Reasoning quotes the things it is reasoning about,
 * and one of those things is eventually a credential.
 */
class LiveThinkingRenderer(
    private val uiEngine: AnsiTerminalEngine,
    private val stream: ThinkingStream = Thinking.stream,
    private val channels: ThinkingChannels = CliThinkingChannel.channels,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private var unsubscribe: (() -> Unit)? = null

    /**
     * Starts rendering, replacing the spinner for the duration.
     *
     * The spinner still starts: it carries the header line, and on a surface
     * with no thoughts yet it is the only thing saying the run began. It is
     * stopped by the first thought, because a spinner animating underneath
     * scrolling text is noise.
     */
    fun start(headline: String) {
        stop()
        uiEngine.startSpinner(headline)
        var first = true
        unsubscribe = stream.subscribe { thought ->
            if (depth() == ThinkingDepth.L1) {
                // An outline, not a transcript. The spinner stays and its
                // message becomes whatever is happening right now, so a long
                // step reads as "advance 3 of 25" against a moving frame --
                // the difference between a run that looks alive and one that
                // looks hung. Deeper thoughts are simply not shown here.
                if (thought.depth == ThinkingDepth.L1) {
                    uiEngine.updateSpinner(redactionFilter.redact(thought.text))
                }
                return@subscribe
            }

            // The indicator keeps running at every depth.
            //
            // It used to stop the moment the first L2 thought arrived, on the
            // theory that an animation under scrolling text is noise. In
            // practice that left a long run with nothing moving on screen
            // between one line of output and the next -- and the gap between
            // two lines is exactly when an operator asks whether the thing has
            // hung. The indicator renders on its own row beneath the
            // transcript, so it does not sit under anything.
            if (!visible(thought)) return@subscribe
            first = false
            uiEngine.updateSpinner(redactionFilter.redact(thought.text))
            uiEngine.renderNotice(format(thought))
        }
    }

    fun stop() {
        unsubscribe?.invoke()
        unsubscribe = null
        uiEngine.stopSpinner()
    }

    /** Re-renders what was already emitted, for an operator who deepened late. */
    fun replay() {
        stream.replay(depth()).forEach { uiEngine.renderNotice(format(it)) }
    }

    fun depth(): ThinkingDepth = channels.depthFor(SURFACE)

    /**
     * Sets the CLI's depth and replays at the new one.
     *
     * Replaying matters: an operator who types `/thinking 3` twelve minutes
     * into a run is asking what has been happening, not only what happens next.
     * Without the replay they would have to wait for the next line to see any
     * benefit, which on a slow step could be minutes.
     */
    fun expand(depth: ThinkingDepth) {
        channels.expand(SURFACE, depth)
    }

    private fun visible(thought: StreamedThought): Boolean = depth().includes(thought.depth)

    /**
     * The line, indented by the depth it came from.
     *
     * The depth used to be stamped as a literal `L1`/`L2` column down the left
     * of every line, which read as debug output leaking into the interface —
     * two characters of machine label in front of every sentence. Indentation
     * carries the same information the way a reader already understands
     * nesting, and costs no words.
     */
    private fun format(thought: StreamedThought): String {
        val body = redactionFilter.redact(thought.text)
        val nesting = (thought.depth.level - 1).coerceAtLeast(0)
        return "  ".repeat(nesting) + body
    }

    private companion object {
        const val SURFACE = "cli"
    }
}

/**
 * The CLI's depth, held where both the renderer and the command can reach it.
 *
 * One instance rather than one per handler: `/thinking 3` and the renderer
 * reading the depth have to be talking about the same channel, and two
 * [ThinkingChannels] would mean the command set a depth nothing rendered at.
 */
object CliThinkingChannel {
    val channels: ThinkingChannels = ThinkingChannels()
}
