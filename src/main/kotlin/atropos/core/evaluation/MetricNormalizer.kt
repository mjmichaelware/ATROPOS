/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import kotlin.math.abs
import kotlin.math.min

/**
 * Turns a raw metric value into a comparable distance, and repairs the defect
 * Source Doc 3 item 59 names.
 *
 * > 59. Correct normalization — repair the zero-target division flaw in
 * > lower-is-better metrics.
 *
 * The flaw is specific. The obvious normalisation for a lower-is-better metric
 * is `value / target`, which is undefined when the target is zero — and the one
 * metric whose target must be exactly zero is [MetricId.SECRET_SAFETY], because
 * §4.2 requires that any confirmed leak blocks release. So the naive formula
 * divides by zero on precisely the safety-critical metric, and depending on the
 * language either produces `Infinity`, `NaN`, or a crash inside the release
 * gate. `NaN` is the worst of the three: every comparison against it is false,
 * so a leak silently reads as *not worse than target* and the gate passes.
 *
 * The repair is to normalise against the distance between target and worst
 * rather than against the target itself. That is defined for a zero target, is
 * direction-agnostic, and gives 0.0 on target and 1.0 at the worst observable
 * value for every metric in the catalogue.
 *
 * All arithmetic for metrics lives here. A calculator that normalised its own
 * values would be a second opinion on what "better" means, and two opinions is
 * how a dashboard and a release gate come to disagree about the same run.
 */
object MetricNormalizer {

    /**
     * Distance from target, in `[0.0, 1.0]`.
     *
     * 0.0 means on or past target; 1.0 means at or beyond the worst value the
     * metric can take. `NaN` in means `NaN` out — an unmeasured metric has no
     * distance, and inventing one would let a subsystem that never ran score.
     */
    fun distance(id: MetricId, value: Double): Double {
        if (value.isNaN()) return Double.NaN
        if (onTarget(id, value)) return 0.0

        val span = abs(id.worst - id.target)
        if (span == 0.0) {
            // Target and worst coincide, so any deviation is total. Reached only
            // by a misconfigured catalogue entry; returning 1.0 keeps the gate
            // fail-closed rather than dividing by zero.
            return 1.0
        }
        return min(1.0, abs(value - id.target) / span)
    }

    /**
     * True when the value meets or beats its target.
     *
     * Uses the declared direction rather than inferring it from the numbers,
     * which is what keeps a lower-is-better metric from reading as improved
     * because its value went up.
     */
    fun onTarget(id: MetricId, value: Double): Boolean {
        if (value.isNaN()) return false
        return when (id.direction) {
            MetricDirection.HIGHER -> value >= id.target - EPSILON
            MetricDirection.LOWER -> value <= id.target + EPSILON
        }
    }

    /**
     * Whether [after] is an improvement on [before].
     *
     * This is the comparison Phase 20's `I(p)` is built on, and it is
     * direction-aware for the same reason [onTarget] is. Equal values are not
     * an improvement: a change that moved nothing must not be promoted as
     * though it did.
     */
    fun improved(id: MetricId, before: Double, after: Double): Boolean {
        if (before.isNaN() || after.isNaN()) return false
        return when (id.direction) {
            MetricDirection.HIGHER -> after > before + EPSILON
            MetricDirection.LOWER -> after < before - EPSILON
        }
    }

    /**
     * A score in `[0.0, 1.0]` where 1.0 is on target.
     *
     * The inverse of [distance], provided because a dashboard reads better in
     * scores and a gate reads better in distances, and deriving one from the
     * other in two places is how they drift apart.
     */
    fun score(id: MetricId, value: Double): Double {
        val distance = distance(id, value)
        return if (distance.isNaN()) Double.NaN else 1.0 - distance
    }

    /** Renders a value in its declared unit. Presentation only. */
    fun format(id: MetricId, value: Double): String = when {
        value.isNaN() -> "unmeasured"
        id.unit == MetricUnit.RATIO -> "%.1f%%".format(value * 100)
        id.unit == MetricUnit.MILLIS -> "%.0fms".format(value)
        else -> if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)
    }

    /**
     * Tolerance for floating-point comparison.
     *
     * Ratios are computed as divisions and a rate that should be exactly 1.0
     * arrives as 0.9999999999999998 often enough that an exact comparison would
     * report a perfect run as off target.
     */
    private const val EPSILON = 1e-9
}
