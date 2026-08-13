/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Whether providers know where they are, and how fast we notice when they stop.
 *
 * > identity recognition accuracy · context attestation success rate ·
 * > drift detection latency
 *
 * Source Doc 3 §3 makes identity, attestation and drift first-class runtime
 * primitives with typed failures. These are the same three primitives seen from
 * the evaluation side: not *did this call attest*, which the gate answers, but
 * *how often does attestation hold*, which is what tells you whether the
 * primitive is working or merely present.
 *
 * Drift latency is the odd one: a duration, lower-is-better, and only
 * measurable on drift that was actually detected. Undetected drift contributes
 * nothing to the latency figure, which means a fast-looking latency over few
 * detections is not reassuring on its own — hence [MetricId.minimumSample] and
 * the sample size travelling with the value.
 */
class ContextMetrics : MetricCalculator {

    override val produces = setOf(
        MetricId.IDENTITY_RECOGNITION,
        MetricId.CONTEXT_ATTESTATION_SUCCESS,
        MetricId.DRIFT_DETECTION_LATENCY
    )

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> = listOf(
        rate(evidence, ObservationKind.IDENTITY_CHECK, MetricId.IDENTITY_RECOGNITION,
            "provider replies returning the correct ATROPOS identity"),
        rate(evidence, ObservationKind.ATTESTATION, MetricId.CONTEXT_ATTESTATION_SUCCESS,
            "context envelopes that attested"),
        driftLatency(evidence)
    )

    private fun rate(
        evidence: MetricEvidence,
        kind: ObservationKind,
        id: MetricId,
        what: String
    ): AtroposMetric {
        val (successes, total) = evidence.rateOf(kind)
        if (total == 0) return AtroposMetric.unmeasured(id, "no $what recorded")
        return AtroposMetric(
            id = id,
            value = successes.toDouble() / total,
            sampleSize = total,
            evidenceHashes = evidence.evidenceStore.putAll(
                evidence.of(kind).filter { !it.success }.map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("$what: $successes of $total") },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = "$successes of $total $what"
        )
    }

    /**
     * Mean milliseconds from drift occurring to drift being detected.
     *
     * Mean rather than median because the tail is the point: a detector that is
     * usually instant and occasionally takes a minute is a detector that will
     * miss the one that matters, and a median hides exactly that.
     */
    private fun driftLatency(evidence: MetricEvidence): AtroposMetric {
        val detections = evidence.of(ObservationKind.DRIFT_DETECTION).filter { it.success }
        if (detections.isEmpty()) {
            return AtroposMetric.unmeasured(
                MetricId.DRIFT_DETECTION_LATENCY,
                "no drift was detected; latency is undefined without a detection"
            )
        }
        val mean = detections.sumOf { it.value } / detections.size
        return AtroposMetric(
            id = MetricId.DRIFT_DETECTION_LATENCY,
            value = mean,
            sampleSize = detections.size,
            evidenceHashes = evidence.evidenceStore.putAll(
                detections.map { it.rawEvidence.ifBlank { it.detail } },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = "mean over ${detections.size} detection(s), worst %.0fms"
                .format(detections.maxOf { it.value })
        )
    }
}
