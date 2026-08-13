/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Produces one metric family from raw evidence.
 *
 * Source Doc 3 §4.3 requires `MetricCalculator` and one small file per metric
 * family as separate files. This is the contract they share, and it is
 * deliberately narrow: a calculator takes evidence and returns values. It does
 * not normalise (that is [MetricNormalizer]), does not classify (that is
 * [ClassificationCalculator]), and does not decide whether the run may ship.
 *
 * The separation is what makes anti-gaming possible. A calculator that could
 * also set its own thresholds could pass itself, and §4.4 requires that "metric
 * definitions, fixtures, environments, and scoring must be repeatable and
 * auditable" — which they cannot be if the thing producing a number also
 * decides what the number means.
 *
 * Implementations must be pure functions of their input. Given the same
 * evidence they return the same values, including the same evidence hashes,
 * because a metric that varies between two runs over identical evidence cannot
 * support a before/after comparison — and Phase 20's `I(p)` is exactly such a
 * comparison.
 */
interface MetricCalculator {

    /** Which metrics this calculator produces. Declared so gaps are detectable. */
    val produces: Set<MetricId>

    /**
     * Computes this family's metrics.
     *
     * A calculator with insufficient evidence must return
     * [AtroposMetric.unmeasured] for the affected ids rather than omitting them
     * or returning zero. Omitting hides the gap; returning zero makes an
     * uninstrumented subsystem indistinguishable from a failing one.
     */
    fun calculate(evidence: MetricEvidence): List<AtroposMetric>
}

/**
 * The observations a calculator reads.
 *
 * One shape for every family, so adding a calculator does not mean adding a
 * parameter to every call site. Fields a given family does not use stay empty;
 * a calculator that finds its inputs empty reports unmeasured rather than
 * guessing.
 *
 * @param evidenceStore where raw observations are persisted and hashed. A
 *   calculator writes what it measured from, then cites the hashes, which is
 *   what makes the resulting metric supported in the §4.1 sense.
 */
data class MetricEvidence(
    val evidenceStore: EvidenceStore,
    val observations: List<Observation> = emptyList()
) {
    /** Observations of one kind, which is how a family selects its inputs. */
    fun of(kind: ObservationKind): List<Observation> = observations.filter { it.kind == kind }

    /** Convenience for the common rate: successes over attempts. */
    fun rateOf(kind: ObservationKind): Pair<Int, Int> {
        val relevant = of(kind)
        return relevant.count { it.success } to relevant.size
    }
}

/**
 * One thing that happened, reduced to what a metric needs.
 *
 * Deliberately not an [atropos.core.observability.ExecutionEvent]: an event
 * records what occurred, an observation records how it scored. Keeping them
 * apart stops the metric layer from reaching into the event schema and stops
 * the event schema from growing scoring fields it has no business holding.
 *
 * @param value a magnitude for metrics that measure one — latency in
 *   milliseconds, tokens spent. Ignored by rate metrics, which read [success].
 */
data class Observation(
    val kind: ObservationKind,
    val success: Boolean,
    val value: Double = 0.0,
    val detail: String = "",
    val rawEvidence: String = ""
)

/** What an observation is about. One per metric family input. */
enum class ObservationKind {
    RESTART,
    VERIFIER_CATCH,
    MODEL_ESCALATION,
    TOKEN_SPEND,
    VERIFIED_CHANGE,
    TERRITORY_CHECK,
    SECRET_SCAN,
    IDENTITY_CHECK,
    ATTESTATION,
    DRIFT_DETECTION,
    TRACE_EVENT,
    CARD_COPY,
    PREVIEW_LAUNCH,
    DETERMINISM_REPLAY,
    REPAIR,
    BATCH,
    ROUTE_SELECTION
}
