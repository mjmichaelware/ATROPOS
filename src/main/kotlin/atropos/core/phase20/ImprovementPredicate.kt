/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.MetricId
import atropos.core.evaluation.MetricNormalizer

/**
 * `I(p)` — `P20-L13` and `P20-NS03`, the metric-space improvement predicate.
 *
 * > P20-NS03 Metric-space improvement I(p): distance to target decreases ∧
 * > guardrails preserved.
 *
 * Two conjuncts, and the second is the one that gets dropped. A change that
 * improves its declared metric while degrading something else is not an
 * improvement; it is a trade made without anyone deciding to make it. Law 20.13
 * requires measurable improvement against a predeclared baseline, and this is
 * where "measurable" and "predeclared" become checkable rather than asserted.
 *
 * Declared before mutation, evaluated after. A proposal that names its metric
 * only once the result is known can always name the one that moved, which is
 * `P20-G09`-shaped gaming — so [ImprovementProposal.metric] is required at
 * proposal time and this compares against it, not against whatever looks best.
 *
 * Guardrails are checked as *no regression*, not as *improvement*. Demanding
 * that everything improve at once would make every proposal fail; permitting
 * any regression would make the conjunct meaningless. No-regression is the line
 * that leaves the loop able to act while keeping it from trading away safety.
 */
object ImprovementPredicate {

    /**
     * Evaluates `I(p)`.
     *
     * @param declared the metric named in the proposal before the change.
     * @param observed what that metric reads after the change.
     * @param guardrailsBefore metrics that must not regress, as measured
     *   before.
     * @param guardrailsAfter the same metrics, after.
     */
    fun evaluate(
        declared: MetricDeclaration,
        observed: Double,
        guardrailsBefore: List<AtroposMetric> = emptyList(),
        guardrailsAfter: List<AtroposMetric> = emptyList()
    ): ImprovementVerdict {
        if (!declared.isDeclared()) {
            return ImprovementVerdict(
                false,
                "metric was not declared with a distinct baseline and target; " +
                    "law 20.6 requires the declaration before the mutation",
                Double.NaN,
                emptyList()
            )
        }
        if (observed.isNaN()) {
            return ImprovementVerdict(false, "metric ${declared.name} was not measured after the change", Double.NaN, emptyList())
        }

        val moved = declared.improvedBy(observed)
        val closed = distanceClosed(declared, observed)
        val regressions = regressions(guardrailsBefore, guardrailsAfter)

        return when {
            !moved -> ImprovementVerdict(
                false,
                "${declared.name} did not move toward target: " +
                    "baseline ${declared.baselineValue}, observed $observed, target ${declared.targetValue}",
                closed,
                regressions
            )

            regressions.isNotEmpty() -> ImprovementVerdict(
                false,
                "${declared.name} improved but guardrails regressed: " + regressions.joinToString("; "),
                closed,
                regressions
            )

            else -> ImprovementVerdict(
                true,
                "${declared.name} moved from ${declared.baselineValue} to $observed " +
                    "(%.1f%% of the distance to target) with no guardrail regression".format(closed * 100),
                closed,
                emptyList()
            )
        }
    }

    /**
     * The share of the declared distance that was closed.
     *
     * Reported rather than merely thresholded because law 20.13 asks for
     * *measurable* improvement: a change that closed 2% of the gap and one that
     * closed 90% both pass the boolean, and the loop's own weighting should be
     * able to tell them apart.
     */
    private fun distanceClosed(declared: MetricDeclaration, observed: Double): Double {
        val span = declared.targetValue - declared.baselineValue
        if (span == 0.0) return 0.0
        return ((observed - declared.baselineValue) / span).coerceIn(0.0, 1.0)
    }

    /**
     * Guardrail metrics that got worse.
     *
     * Direction-aware through [MetricNormalizer], so a lower-is-better
     * guardrail is not reported as regressed because its number fell.
     */
    private fun regressions(
        before: List<AtroposMetric>,
        after: List<AtroposMetric>
    ): List<String> {
        if (before.isEmpty() || after.isEmpty()) return emptyList()
        val previous: Map<MetricId, AtroposMetric> = before.associateBy { it.id }
        return after.mapNotNull { current ->
            val prior = previous[current.id] ?: return@mapNotNull null
            if (prior.value.isNaN() || current.value.isNaN()) return@mapNotNull null
            val worse = MetricNormalizer.improved(current.id, current.value, prior.value)
            if (worse) {
                "${current.id.canonical} ${MetricNormalizer.format(current.id, prior.value)} -> " +
                    MetricNormalizer.format(current.id, current.value)
            } else {
                null
            }
        }
    }
}

/**
 * Whether a promoted change earned its promotion.
 *
 * [distanceClosed] travels with the boolean so the improvement loop can weight
 * by how much moved, and so a proposal that technically passed by a rounding
 * error is visible as such in the ledger.
 */
data class ImprovementVerdict(
    val holds: Boolean,
    val reason: String,
    val distanceClosed: Double,
    val guardrailRegressions: List<String>
) {
    fun render(): String = (if (holds) "I(p) holds" else "I(p) fails") + ": " + reason
}
