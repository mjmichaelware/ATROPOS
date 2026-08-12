/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

/**
 * The three ways a run can be stopped.
 *
 * `SUP.UX.INTERRUPT-PRIMITIVE` names them and states the predicate they exist
 * to satisfy: "Interrupt is a first-class, recoverable session primitive rather
 * than process death." Competitors treat Ctrl-C as kill, which on a long phone
 * job means the work is gone and the operator learns not to interrupt at all.
 *
 * The distinction that matters is what survives:
 *
 * [SOFT] lets the current tool finish. Nothing is lost because nothing was
 * cut — the run stops at the next boundary, which is the only point where its
 * state is already consistent.
 *
 * [HARD] stops the current tool now. The run is abandoned mid-step, so the
 * durable record is whatever the last completed step wrote; that is a real
 * loss and is reported as one rather than presented as a clean stop.
 *
 * [FREEZE] stops and preserves the full run state so it can be resumed at the
 * exact DAG position. This is the level the atom is really about, and the one
 * that requires the run to have somewhere durable to be written to.
 */
enum class InterruptLevel(
    val canonical: String,
    val description: String,
    /** True when the run can be continued from where it stopped. */
    val resumable: Boolean
) {
    SOFT(
        "soft",
        "finish the current tool, then stop at the next boundary",
        resumable = true
    ),
    HARD(
        "hard",
        "stop the current tool now; work in flight is abandoned",
        resumable = false
    ),
    FREEZE(
        "freeze",
        "stop and preserve full run state for exact resume",
        resumable = true
    );

    companion object {
        fun fromCanonical(term: String): InterruptLevel? =
            entries.firstOrNull { it.canonical.equals(term.trim(), ignoreCase = true) }
    }
}
