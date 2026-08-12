/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

/**
 * A step list that is also a live narration.
 *
 * `SUP.UX.INTERRUPT-PRIMITIVE` is about being able to stop a long run;
 * this is about being able to *watch* one. Both exist because a
 * fourteen-minute run that shows nothing has already failed the operator,
 * whatever it eventually returns.
 *
 * The engine already built the narrative — every long-running chain in this
 * codebase accumulates a `steps` list and returns it at the end. The defect was
 * never missing information; it was that the information arrived only after
 * there was nothing left to decide.
 *
 * So this is a `List<String>` that publishes on append. Existing call sites
 * keep working verbatim — `steps += "..."` reads the same and now also reaches
 * the operator's screen — which matters because the alternative was editing
 * thirty call sites and getting the depth wrong at some of them.
 *
 * Depth defaults to [ThinkingDepth.L2], the reasoning level. A step is what the
 * engine did and why, which is exactly what L2 means. [outline] and [detail]
 * exist for the few lines that belong at the other two depths.
 */
class NarratedSteps(
    private val stream: ThinkingStream = Thinking.stream,
    private val backing: MutableList<String> = mutableListOf()
) : List<String> by backing {

    /** Appends a reasoning step and publishes it at L2. */
    operator fun plusAssign(text: String) {
        backing += text
        stream.emit(ThinkingDepth.L2, text)
    }

    /**
     * A milestone: the handful of lines that answer "where is it up to?".
     *
     * L1 is what an operator sees without expanding anything, so it has to stay
     * scarce. A milestone for every step is the same as no milestones.
     */
    fun outline(text: String) {
        backing += text
        stream.emit(ThinkingDepth.L1, text)
    }

    /**
     * Detail that only matters when something has gone wrong.
     *
     * Recorded in the steps either way — the evidence bundle should carry it
     * whether or not anyone was watching at L3.
     */
    fun detail(text: String) {
        backing += text
        stream.emit(ThinkingDepth.L3, text)
    }

    /** The plain list, for a result object that should not carry a stream. */
    fun frozen(): List<String> = backing.toList()
}
