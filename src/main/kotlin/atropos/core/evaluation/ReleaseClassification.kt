/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * The five outcomes Source Doc 3 §4.2 requires a release gate to separate.
 *
 * > 60. Correct release gates — clearly separate score reduction, minimum
 * > failure, competitive failure, frontier failure, and safety hard failure.
 *
 * The existing gate has three severities — INFO, WARNING, BLOCKER — which
 * collapses four of these into one. That collapse is the thing the requirement
 * is written against: a run that is merely below frontier and a run that leaked
 * a secret are both "BLOCKER", so an operator cannot tell from the verdict
 * whether to tune a weight or stop the release and investigate.
 *
 * Ordered by severity so a verdict is the maximum of its parts, and so
 * comparisons read the way they are spoken — a frontier failure is worse than a
 * competitive one.
 */
enum class ReleaseClassification(val severity: Int, val blocksRelease: Boolean, val label: String) {

    /** Everything on target. */
    PASS(0, false, "pass"),

    /**
     * Off target, but above every declared floor.
     *
     * The only outcome that is a tuning signal rather than a stop. Phase 20's
     * self-improvement loop is allowed to act on this class and no other.
     */
    SCORE_REDUCTION(1, false, "score reduction"),

    /** Below the minimum a release must clear at all. */
    MINIMUM_FAILURE(2, true, "minimum failure"),

    /** Above minimum but below what the competitive set achieves. */
    COMPETITIVE_FAILURE(3, true, "competitive failure"),

    /** Above competitive but below the frontier target. */
    FRONTIER_FAILURE(4, true, "frontier failure"),

    /**
     * A safety invariant broke.
     *
     * Secret leakage, territory violation, verification bypass, restart
     * corruption. Source Doc 3 §4.2 and Part C §7 both make this class
     * release-blocking and reviewable by a human rather than by the loop —
     * which is why it is a separate class and not the top of a severity scale.
     */
    SAFETY_HARD_FAILURE(5, true, "safety hard failure");

    /** True when the improvement loop may act on this class without a human. */
    val actionableByLoop: Boolean get() = this == SCORE_REDUCTION

    companion object {
        /** The worse of two classifications. */
        fun worst(a: ReleaseClassification, b: ReleaseClassification): ReleaseClassification =
            if (a.severity >= b.severity) a else b

        /** The worst in a collection, or [PASS] when empty. */
        fun worst(all: Collection<ReleaseClassification>): ReleaseClassification =
            all.fold(PASS, ::worst)
    }
}

/**
 * The floors a metric must clear for each failure class.
 *
 * Held as data rather than as branches inside the calculator, so changing what
 * "competitive" means is an edit to a table and not to control flow — and so
 * the thresholds can be printed, which §4.4 anti-gaming requires: a metric
 * whose thresholds cannot be shown cannot be audited for having been moved.
 *
 * Expressed as scores in `[0.0, 1.0]` from [MetricNormalizer.score], so one
 * table covers both directions and every unit.
 */
data class ReleaseThresholds(
    val minimum: Double = DEFAULT_MINIMUM,
    val competitive: Double = DEFAULT_COMPETITIVE,
    val frontier: Double = DEFAULT_FRONTIER
) {
    init {
        require(minimum in 0.0..1.0 && competitive in 0.0..1.0 && frontier in 0.0..1.0) {
            "release thresholds must be scores in [0,1]"
        }
        require(minimum <= competitive && competitive <= frontier) {
            "release thresholds must ascend: minimum <= competitive <= frontier"
        }
    }

    fun render(): String =
        "minimum=%.2f competitive=%.2f frontier=%.2f".format(minimum, competitive, frontier)

    companion object {
        const val DEFAULT_MINIMUM = 0.50
        const val DEFAULT_COMPETITIVE = 0.75
        const val DEFAULT_FRONTIER = 0.95
    }
}
