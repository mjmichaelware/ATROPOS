/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Traversal state for arrow-key recall over a [PromptHistoryRing].
 *
 * Separate from the ring because retention and traversal answer different
 * questions. The ring knows what is remembered; this knows where the operator
 * currently is inside it, which lane they entered from, and what half-typed
 * line they left behind.
 *
 * ## The saved draft is the reason this holds state at all
 *
 * Pressing Up on a partially typed line has to be undoable. Stepping back down
 * past the newest entry must restore exactly what was being typed, not an empty
 * prompt — otherwise recall silently destroys work, which is the failure mode
 * that makes people stop trusting history navigation. [attach] captures the
 * draft on the first step away from it and [down] hands it back at the end.
 *
 * Switching lanes mid-traversal re-attaches rather than continuing, because a
 * position in the slash ring means nothing in the shell ring.
 */
class PromptHistoryBrowser(private val ring: PromptHistoryRing) {

    private var stepsBack = DETACHED
    private var attachedLane: PromptHistoryLane? = null
    private var savedDraft = ""

    /** True while the buffer is showing recalled text rather than the operator's own draft. */
    val isAttached: Boolean get() = stepsBack != DETACHED

    /** The lane currently being traversed, or null when detached. */
    val lane: PromptHistoryLane? get() = attachedLane

    /**
     * Steps one entry further back in [lane].
     *
     * [currentText] is retained as the restorable draft when this call is the
     * one that leaves the operator's own line.
     */
    fun up(lane: PromptHistoryLane, currentText: String): PromptHistoryMove {
        if (ring.isEmpty(lane)) return PromptHistoryMove.None

        if (stepsBack == DETACHED || attachedLane != lane) {
            attach(lane, currentText)
        }

        val furthest = ring.size(lane) - 1
        if (stepsBack < furthest) {
            stepsBack++
        }

        val recalled = ring.recall(lane, stepsBack) ?: return PromptHistoryMove.None
        return PromptHistoryMove.Recalled(recalled)
    }

    /**
     * Steps one entry forward, or back onto the saved draft when already at the
     * newest entry.
     */
    fun down(): PromptHistoryMove {
        val lane = attachedLane ?: return PromptHistoryMove.None
        return when {
            stepsBack > 0 -> {
                stepsBack--
                ring.recall(lane, stepsBack)
                    ?.let(PromptHistoryMove::Recalled)
                    ?: PromptHistoryMove.None
            }

            stepsBack == 0 -> {
                val draft = savedDraft
                detach()
                PromptHistoryMove.RestoredDraft(draft)
            }

            else -> PromptHistoryMove.None
        }
    }

    /**
     * Jumps to the newest entry in [lane] containing [needle].
     *
     * The result is left detached: an incremental search lands the operator on
     * a line they intend to edit, so the next keystroke should be an edit rather
     * than a continuation of traversal.
     */
    fun search(lane: PromptHistoryLane, needle: String): PromptHistoryMove {
        val match = ring.searchBackwards(lane, needle) ?: return PromptHistoryMove.None
        attachedLane = lane
        stepsBack = DETACHED
        return PromptHistoryMove.Recalled(match)
    }

    /**
     * Abandons traversal, keeping whatever text is currently displayed.
     *
     * Called when the operator edits the line: once they have changed it, it is
     * their draft again and stepping down should not overwrite it with a stale
     * saved copy.
     */
    fun detach() {
        stepsBack = DETACHED
        attachedLane = null
        savedDraft = ""
    }

    private fun attach(lane: PromptHistoryLane, currentText: String) {
        savedDraft = currentText
        stepsBack = DETACHED
        attachedLane = lane
    }

    private companion object {
        /** Not currently inside the history — the buffer holds the operator's own line. */
        const val DETACHED = -1
    }
}

/** What a traversal step asks the prompt buffer to display. */
sealed interface PromptHistoryMove {

    /** Nothing to move to; the buffer keeps what it has. */
    data object None : PromptHistoryMove

    /** Show a remembered line. */
    data class Recalled(val text: String) : PromptHistoryMove

    /** Put the operator's own half-typed line back. */
    data class RestoredDraft(val text: String) : PromptHistoryMove
}
