/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * `P20-H06` and `P20-NS04` — the proof that the loop stops.
 *
 * > P20-H06 Termination ranking: lexicographic potential → 0 stops loop.
 * > P20-NS04 Termination ranking function: potential guarantees no infinite
 * > cycle.
 *
 * A self-improvement loop is a rewriting system, and a rewriting system with no
 * ranking function has no argument that it terminates. The usual failure is not
 * an obvious infinite loop but oscillation: subsystem A is changed to improve a
 * metric, which degrades B, which is changed back, and each step is individually
 * justified. `P20-G08` names that case directly.
 *
 * The potential is lexicographic rather than a weighted sum, which matters. A
 * sum lets a large improvement in one component mask an increase in another, so
 * the total falls while the loop is in fact making no progress on the thing
 * that would end it. Lexicographic comparison forbids that: a later component
 * can only break a tie in an earlier one.
 *
 * Ordering, most significant first:
 *
 * 1. **Open deficiencies.** The loop exists to close these; nothing else can
 *    end it.
 * 2. **Quarantined proposals.** Bounded above and only removable by new
 *    evidence, so they cannot be manufactured to keep the potential positive.
 * 3. **Open observation periods.** Strictly decreasing with time.
 * 4. **Remaining budget.** Decreases monotonically within a period.
 *
 * Every component is a non-negative integer and every legal transition
 * decreases the tuple lexicographically or leaves it unchanged while consuming
 * budget — which is itself component four. Zero means there is nothing left to
 * do, and the loop stops.
 */
data class TerminationPotential(
    val openDeficiencies: Int,
    val quarantinedProposals: Int,
    val openObservationPeriods: Int,
    val remainingBudgetUnits: Int
) {
    init {
        require(openDeficiencies >= 0 && quarantinedProposals >= 0) {
            "potential components cannot be negative"
        }
        require(openObservationPeriods >= 0 && remainingBudgetUnits >= 0) {
            "potential components cannot be negative"
        }
    }

    /** The loop terminates here. */
    val isZero: Boolean
        get() = openDeficiencies == 0 &&
            quarantinedProposals == 0 &&
            openObservationPeriods == 0 &&
            remainingBudgetUnits == 0

    /** The components in significance order, for comparison and for display. */
    fun components(): List<Int> =
        listOf(openDeficiencies, quarantinedProposals, openObservationPeriods, remainingBudgetUnits)

    /**
     * Lexicographic comparison.
     *
     * @return negative when this is strictly smaller, which is the direction a
     *   legal transition must move.
     */
    operator fun compareTo(other: TerminationPotential): Int {
        components().zip(other.components()).forEach { (mine, theirs) ->
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    fun render(): String =
        "potential=(${components().joinToString(", ")})" + if (isZero) " TERMINATES" else ""
}

/**
 * Checks that the loop is actually descending.
 *
 * The ranking function is only a termination proof if something enforces it.
 * A transition that leaves the potential equal or larger is a transition that
 * has made no progress, and permitting it is how oscillation begins — so this
 * is checked on every iteration rather than reasoned about once.
 */
object TerminationRanking {

    /**
     * Whether moving from [before] to [after] is a legal step.
     *
     * Equal potentials are legal exactly once, tracked by [consecutiveStalls]:
     * a cycle that consumed budget without changing any count is doing work
     * that has not landed yet. Two in a row is a stall, and a stall is how an
     * unbounded loop looks from inside.
     */
    fun step(
        before: TerminationPotential,
        after: TerminationPotential,
        consecutiveStalls: Int = 0
    ): TerminationStep = when {
        after < before -> TerminationStep(
            legal = true, terminated = after.isZero, stalls = 0,
            reason = "potential descended ${before.render()} -> ${after.render()}"
        )

        after > before -> TerminationStep(
            legal = false, terminated = false, stalls = consecutiveStalls + 1,
            reason = "potential increased ${before.render()} -> ${after.render()}; " +
                "no legal transition raises it"
        )

        consecutiveStalls + 1 >= MAX_STALLS -> TerminationStep(
            legal = false, terminated = true, stalls = consecutiveStalls + 1,
            reason = "potential unchanged for ${consecutiveStalls + 1} iterations; stopping to avoid oscillation"
        )

        else -> TerminationStep(
            legal = true, terminated = false, stalls = consecutiveStalls + 1,
            reason = "potential unchanged; one stall tolerated for work in flight"
        )
    }

    /** Consecutive unchanged iterations before the loop stops itself. */
    const val MAX_STALLS = 2
}

/** The verdict on one iteration of the loop. */
data class TerminationStep(
    val legal: Boolean,
    val terminated: Boolean,
    val stalls: Int,
    val reason: String
) {
    /** True when the loop should run again. */
    val continues: Boolean get() = legal && !terminated
}

interface TerminationRankingFunction {
    fun evaluate(potential: TerminationPotential): Boolean
}
