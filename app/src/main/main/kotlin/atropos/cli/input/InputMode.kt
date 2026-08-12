/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * How much the runtime may do with the next submitted line.
 *
 * The order is deliberate and increasing: ASK answers, PLAN proposes, AUTOPILOT
 * acts. [next] cycles in that direction so a single key walks the operator from
 * least to most agency and then wraps — the escalation is always visible and
 * always reversible by continuing to press.
 */
enum class InputMode {
    /** Answer the question. No mutation. */
    ASK,

    /** Produce a plan for review rather than executing it. */
    PLAN,

    /** Execute within the bounds already granted. */
    AUTOPILOT;

    /** The next mode in the cycle, wrapping from the last back to the first. */
    fun next(): InputMode = entries[(ordinal + 1) % entries.size]
}
