/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import atropos.core.observability.RunExport

/**
 * Whether the record of a run is any good.
 *
 * > trace completeness · copy fidelity · event determinism · preview success
 *
 * Every metric here measures the observability layer rather than the work, and
 * that is the point: Source Doc 3 §4.1 counts an incomplete trace as a defect
 * in its own right. A run that succeeded and cannot prove it is not a success
 * under the Blueprint's own definition, which requires evidence rather than
 * elapsed time.
 *
 * Trace completeness reads straight from [RunExport.traceCompleteness], which
 * computes it where the events are. Recomputing it here would be a second
 * opinion on the same question, and the two would eventually disagree.
 */
class TraceMetrics : MetricCalculator {

    override val produces = setOf(
        MetricId.TRACE_COMPLETENESS,
        MetricId.COPY_FIDELITY,
        MetricId.EVENT_DETERMINISM,
        MetricId.PREVIEW_SUCCESS
    )

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> = listOf(
        traceCompleteness(evidence),
        copyFidelity(evidence),
        rate(evidence, ObservationKind.DETERMINISM_REPLAY, MetricId.EVENT_DETERMINISM,
            "replays reproducing an identical event sequence"),
        rate(evidence, ObservationKind.PREVIEW_LAUNCH, MetricId.PREVIEW_SUCCESS,
            "previews that launched and rendered")
    )

    /**
     * Computes trace completeness from run exports rather than observations.
     *
     * Offered as a separate entry point because the input is a run, not a list
     * of scored events — and because [RunExport] already owns the predicate.
     */
    fun fromExports(evidence: MetricEvidence, exports: List<RunExport>): AtroposMetric {
        if (exports.isEmpty()) {
            return AtroposMetric.unmeasured(MetricId.TRACE_COMPLETENESS, "no runs exported")
        }
        val events = exports.sumOf { it.eventCount }
        if (events == 0) {
            return AtroposMetric.unmeasured(MetricId.TRACE_COMPLETENESS, "runs contained no events")
        }
        val complete = exports.sumOf { export -> export.events.count { it.provenanceComplete } }
        val incomplete = exports.flatMap { it.incompleteEvents() }
        return AtroposMetric(
            id = MetricId.TRACE_COMPLETENESS,
            value = complete.toDouble() / events,
            sampleSize = events,
            evidenceHashes = evidence.evidenceStore.putAll(
                incomplete.take(EVIDENCE_SAMPLE).map { event ->
                    "#${event.sequence} missing ${event.missingProvenance().joinToString(", ")}"
                }.ifEmpty { listOf("all $events events carried complete provenance") },
                EvidenceKind.EXECUTION_EVENT
            ),
            detail = "$complete of $events events carried all seven provenance fields"
        )
    }

    private fun traceCompleteness(evidence: MetricEvidence): AtroposMetric {
        val (complete, total) = evidence.rateOf(ObservationKind.TRACE_EVENT)
        if (total == 0) {
            return AtroposMetric.unmeasured(
                MetricId.TRACE_COMPLETENESS,
                "no trace events observed; use fromExports when reading runs directly"
            )
        }
        return AtroposMetric(
            id = MetricId.TRACE_COMPLETENESS,
            value = complete.toDouble() / total,
            sampleSize = total,
            evidenceHashes = evidence.evidenceStore.putAll(
                listOf("$complete of $total events carried complete provenance"),
                EvidenceKind.EXECUTION_EVENT
            ),
            detail = "$complete of $total events complete"
        )
    }

    /**
     * How much of a card's content survives being copied.
     *
     * Measured as copied bytes over rendered-body bytes, which is why
     * `OutputCard` keeps its body separate from its chrome — the metric is only
     * computable because the two were never mixed.
     */
    private fun copyFidelity(evidence: MetricEvidence): AtroposMetric {
        val copies = evidence.of(ObservationKind.CARD_COPY)
        if (copies.isEmpty()) {
            return AtroposMetric.unmeasured(MetricId.COPY_FIDELITY, "no copy operations observed")
        }
        val faithful = copies.count { it.success }
        return AtroposMetric(
            id = MetricId.COPY_FIDELITY,
            value = faithful.toDouble() / copies.size,
            sampleSize = copies.size,
            evidenceHashes = evidence.evidenceStore.putAll(
                copies.filter { !it.success }.map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("$faithful of ${copies.size} copies were byte-faithful") },
                EvidenceKind.RAW
            ),
            detail = "$faithful of ${copies.size} copies preserved the full body"
        )
    }

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
                EvidenceKind.RAW
            ),
            detail = "$successes of $total $what"
        )
    }

    private companion object {
        const val EVIDENCE_SAMPLE = 50
    }
}
