/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Assigns each metric one of the five release classes, and the run the worst of
 * them.
 *
 * Source Doc 3 item 64 requires minimum, competitive and frontier to be
 * "calculated all three explicitly" rather than inferred from a single verdict.
 * That is why this returns a classification per metric alongside the overall
 * one: an operator asking *why did this fail* needs the metric, not the verdict.
 *
 * Three rules take precedence over the thresholds, and each exists because the
 * naive version of this calculator gets it wrong:
 *
 * 1. **A safety-critical metric off target is a hard failure**, regardless of
 *    score. A single leaked secret scores badly but not catastrophically on a
 *    linear scale, and §4.2 requires it to block absolutely.
 * 2. **An unsupported metric cannot pass.** §4.1 requires every metric to link
 *    to raw immutable evidence and §4.2 forbids unsupported percentages; a
 *    number with no evidence behind it is a claim, and claims must not clear
 *    gates.
 * 3. **An unmeasured metric is not a zero.** A subsystem that never ran and one
 *    that ran and failed everything produce the same number under naive
 *    scoring, and treating them alike is how absent instrumentation reads as
 *    catastrophic failure — or, worse, how it gets excluded and reads as pass.
 */
class ClassificationCalculator(
    private val thresholds: Map<MetricId, ReleaseThresholds> = emptyMap(),
    private val default: ReleaseThresholds = ReleaseThresholds()
) {

    /** The class for one metric, with the reason stated. */
    fun classify(metric: AtroposMetric): MetricClassification {
        val bounds = thresholds[metric.id] ?: default

        if (metric.unmeasured) {
            return MetricClassification(
                metric, ReleaseClassification.MINIMUM_FAILURE, Double.NaN, bounds,
                "metric was not measured; an absent measurement is not a value"
            )
        }
        if (!metric.supported) {
            return MetricClassification(
                metric, ReleaseClassification.MINIMUM_FAILURE, MetricNormalizer.score(metric.id, metric.value), bounds,
                "no evidence hashes; §4.2 forbids unsupported percentages"
            )
        }
        if (metric.id.safetyCritical && !metric.onTarget()) {
            return MetricClassification(
                metric, ReleaseClassification.SAFETY_HARD_FAILURE, MetricNormalizer.score(metric.id, metric.value), bounds,
                "safety-critical metric is off target"
            )
        }
        if (!metric.sufficient) {
            return MetricClassification(
                metric, ReleaseClassification.SCORE_REDUCTION, MetricNormalizer.score(metric.id, metric.value), bounds,
                "sample of ${metric.sampleSize} is below the ${metric.id.minimumSample} this metric needs"
            )
        }

        val score = MetricNormalizer.score(metric.id, metric.value)
        val classification = when {
            score < bounds.minimum -> ReleaseClassification.MINIMUM_FAILURE
            score < bounds.competitive -> ReleaseClassification.COMPETITIVE_FAILURE
            score < bounds.frontier -> ReleaseClassification.FRONTIER_FAILURE
            metric.onTarget() -> ReleaseClassification.PASS
            else -> ReleaseClassification.SCORE_REDUCTION
        }
        return MetricClassification(
            metric, classification, score, bounds,
            "score %.3f against %s".format(score, bounds.render())
        )
    }

    /**
     * The whole run.
     *
     * The overall class is the worst of the parts, never an average. Averaging
     * is how one leaked secret gets diluted by eleven healthy metrics into a
     * passing score, which is the specific outcome §4.2 exists to prevent.
     */
    fun classifyAll(metrics: List<AtroposMetric>): RunClassification {
        val classified = metrics.map(::classify)
        return RunClassification(
            metrics = classified,
            overall = ReleaseClassification.worst(classified.map { it.classification })
        )
    }
}

/** One metric's class, with the score and thresholds that produced it. */
data class MetricClassification(
    val metric: AtroposMetric,
    val classification: ReleaseClassification,
    val score: Double,
    val thresholds: ReleaseThresholds,
    val reason: String
) {
    fun render(): String =
        "${metric.id.canonical}: ${classification.label} · ${metric.render()} · $reason"
}

/** Every metric's class, plus the run's. */
data class RunClassification(
    val metrics: List<MetricClassification>,
    val overall: ReleaseClassification
) {
    val blocksRelease: Boolean get() = overall.blocksRelease

    /** Metrics at or worse than [ReleaseClassification.MINIMUM_FAILURE]. */
    fun blocking(): List<MetricClassification> =
        metrics.filter { it.classification.blocksRelease }

    /** Metrics the improvement loop may act on without a human. */
    fun loopActionable(): List<MetricClassification> =
        metrics.filter { it.classification.actionableByLoop }

    /**
     * Explicitly calculated minimum, competitive and frontier verdicts, which
     * Source Doc 3 item 64 requires as three separate answers rather than one.
     */
    fun tierVerdicts(): Map<String, Boolean> = mapOf(
        "minimum" to metrics.none { it.classification.severity >= ReleaseClassification.MINIMUM_FAILURE.severity },
        "competitive" to metrics.none { it.classification.severity >= ReleaseClassification.COMPETITIVE_FAILURE.severity },
        "frontier" to metrics.none { it.classification.severity >= ReleaseClassification.FRONTIER_FAILURE.severity }
    )

    fun render(): String = buildString {
        appendLine("release classification: ${overall.label}")
        tierVerdicts().forEach { (tier, met) -> appendLine("  $tier: ${if (met) "met" else "not met"}") }
        metrics.sortedByDescending { it.classification.severity }.forEach { appendLine("  " + it.render()) }
    }.trimEnd()
}
