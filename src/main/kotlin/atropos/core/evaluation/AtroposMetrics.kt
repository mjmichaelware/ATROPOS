/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import atropos.core.observability.RunExport

/**
 * Every metric family, run together, with gaps reported rather than hidden.
 *
 * Source Doc 3 §4.3 names `AtroposMetrics` as its own file alongside the
 * per-family calculators. Its job is composition and one rule the families
 * cannot enforce individually: **every metric in the catalogue must appear in
 * the result**, measured or explicitly unmeasured.
 *
 * That rule is the whole reason this file exists. A calculator that quietly
 * omits a metric it could not compute produces a report where absence looks
 * like irrelevance, and a release gate over such a report passes because it
 * never saw the thing that would have failed. §4.2's ban on unsupported
 * percentages is the same principle from the other side: a number with nothing
 * behind it and a number that is not there are both claims, and neither may
 * clear a gate silently.
 */
class AtroposMetrics(
    private val calculators: List<MetricCalculator> = defaultCalculators()
) {

    /**
     * Computes every metric.
     *
     * @return one [AtroposMetric] per [MetricId], in catalogue order. Ids no
     *   calculator claims are returned unmeasured with the reason stated, so a
     *   family that was never wired is visible in the report rather than
     *   inferred from its absence.
     */
    fun computeAll(evidence: MetricEvidence): List<AtroposMetric> {
        val produced = calculators
            .flatMap { calculator -> runCatching { calculator.calculate(evidence) }.getOrElse { failure ->
                // A calculator that throws must not take the report down with
                // it. The metrics it owns become unmeasured with the failure
                // named, which is both honest and non-fatal.
                calculator.produces.map { id ->
                    AtroposMetric.unmeasured(id, "calculator failed: ${reason(failure)}")
                }
            } }
            .associateBy { it.id }

        return MetricId.entries.map { id ->
            produced[id] ?: AtroposMetric.unmeasured(id, "no calculator produces this metric")
        }
    }

    /**
     * Computes with trace completeness read from real run exports.
     *
     * [TraceMetrics] can derive completeness from observations or from exports;
     * the export path is exact because it reads the events themselves, so it
     * replaces the observation-derived value when exports are available.
     */
    fun computeAll(evidence: MetricEvidence, exports: List<RunExport>): List<AtroposMetric> {
        if (exports.isEmpty()) return computeAll(evidence)
        val fromExports = TraceMetrics().fromExports(evidence, exports)
        return computeAll(evidence).map { metric ->
            if (metric.id == MetricId.TRACE_COMPLETENESS) fromExports else metric
        }
    }

    /** Ids in the catalogue that no registered calculator claims. */
    fun uncovered(): Set<MetricId> =
        MetricId.entries.toSet() - calculators.flatMap { it.produces }.toSet()

    /**
     * Ids claimed by more than one calculator.
     *
     * A duplicate claim means two families would each produce a value and one
     * would silently win by map ordering. Reported rather than resolved, since
     * which one is correct is not a decision this file can make.
     */
    fun conflicts(): Map<MetricId, Int> =
        calculators.flatMap { it.produces }
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }

    private fun reason(failure: Throwable): String =
        (failure::class.simpleName ?: "error") + ": " + (failure.message ?: "no message")

    companion object {
        /** The families that exist, one per Source Doc 3 §4.3 file. */
        fun defaultCalculators(): List<MetricCalculator> = listOf(
            RecoveryMetrics(),
            SafetyMetrics(),
            CoordinationMetrics(),
            ContextMetrics(),
            TraceMetrics(),
            DeliveryMetrics()
        )
    }
}
