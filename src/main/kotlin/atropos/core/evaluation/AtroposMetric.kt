/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * One measured quantity, with the evidence that produced it.
 *
 * The evaluation package already owns [EvaluationMetric], which is a *gate*: a
 * boolean with a severity. Source Doc 3 §4.1 asks for something different — a
 * list of quantities, "restart recovery success rate", "coordination efficiency
 * (tokens per verified engineering change)", "drift detection latency" — none
 * of which is a boolean and none of which a gate can express. A rate of 0.87 is
 * not a pass or a fail until somebody says what the target is, and saying that
 * is what [ReleaseClassification] does.
 *
 * The two live side by side rather than one replacing the other. A gate answers
 * *may this release*; a metric answers *how is it doing, and by how much has it
 * moved*. Phase 20's improvement predicate `I(p)` needs the second, because "did
 * it get better" is not answerable from a boolean that was already true.
 *
 * @param evidenceHashes the raw evidence this value was computed from. Source
 *   Doc 3 §4.1 requires every metric to "link to raw immutable evidence" and
 *   §4.2 makes unsupported percentages a release problem, so a metric with no
 *   hashes is not a weaker metric — it is [supported]`= false`, and the
 *   anti-gaming auditor treats it as absent.
 * @param sampleSize how many observations the value came from. A 100% success
 *   rate over one observation and over four hundred are the same number and
 *   very different facts; omitting this is how a metric flatters itself.
 */
data class AtroposMetric(
    val id: MetricId,
    val value: Double,
    val sampleSize: Int,
    val evidenceHashes: List<String> = emptyList(),
    val detail: String = ""
) {
    /** True when this value is backed by raw evidence, per §4.1. */
    val supported: Boolean get() = evidenceHashes.isNotEmpty()

    /** True when there is enough data for the value to mean anything. */
    val sufficient: Boolean get() = sampleSize >= id.minimumSample

    /**
     * Distance to target, normalised so that 0.0 is on target and 1.0 is as far
     * away as the metric can be. Direction-aware; see [MetricNormalizer], which
     * owns the arithmetic including the zero-target case Source Doc 3 item 59
     * calls out as a defect to repair.
     */
    fun distanceToTarget(): Double = MetricNormalizer.distance(id, value)

    /** True when the value meets or beats its target. */
    fun onTarget(): Boolean = MetricNormalizer.onTarget(id, value)

    fun render(): String = buildString {
        append(id.canonical).append('=')
        append(MetricNormalizer.format(id, value))
        append(" n=").append(sampleSize)
        append(" target=").append(MetricNormalizer.format(id, id.target))
        append(if (onTarget()) " ON_TARGET" else " OFF_TARGET")
        if (!supported) append(" UNSUPPORTED")
        if (!sufficient) append(" INSUFFICIENT_SAMPLE")
        if (detail.isNotBlank()) append(" · ").append(detail)
    }

    companion object {
        /**
         * A metric that could not be computed.
         *
         * Distinct from a metric of zero, which is a measurement. An absent
         * measurement reported as zero is how a subsystem that never ran looks
         * identical to one that ran and failed everything.
         */
        fun unmeasured(id: MetricId, why: String) =
            AtroposMetric(id = id, value = Double.NaN, sampleSize = 0, detail = why)
    }
}

/** True when a metric was never computed rather than computed as zero. */
val AtroposMetric.unmeasured: Boolean get() = value.isNaN()
