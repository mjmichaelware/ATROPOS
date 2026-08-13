/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Whether work lands and stays landed.
 *
 * > repair quality (permanent fixes vs recurring failures) ·
 * > batch completion rate and rollback frequency ·
 * > provider route effectiveness over time
 *
 * Source Doc 3 Part C §7 adds these to the §4.1 list, and they share a subject:
 * not whether an action succeeded once, but whether it held. A repair that
 * recurs is not a repair, a batch that rolls back consumed its cost and
 * delivered nothing, and a route that succeeds only after failing over is a
 * route that was chosen wrongly.
 *
 * Rollback frequency is emitted alongside batch completion rather than derived
 * from it, even though the two are usually complements. They stop being
 * complements the moment a batch neither completes nor rolls back — abandoned,
 * interrupted, still open — and that gap is exactly the state worth seeing.
 */
class DeliveryMetrics : MetricCalculator {

    override val produces = setOf(
        MetricId.REPAIR_QUALITY,
        MetricId.BATCH_COMPLETION_RATE,
        MetricId.ROLLBACK_FREQUENCY,
        MetricId.ROUTE_EFFECTIVENESS
    )

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> =
        listOf(repairQuality(evidence)) + batchMetrics(evidence) + listOf(routeEffectiveness(evidence))

    /**
     * Repairs that held, over repairs attempted.
     *
     * A repair counts as successful only if the failure it addressed did not
     * recur. That is a judgement made when the observation is recorded, not
     * here — this calculator cannot know the future, and a metric that guessed
     * would report every fresh repair as permanent.
     */
    private fun repairQuality(evidence: MetricEvidence): AtroposMetric {
        val repairs = evidence.of(ObservationKind.REPAIR)
        if (repairs.isEmpty()) {
            return AtroposMetric.unmeasured(MetricId.REPAIR_QUALITY, "no repairs observed")
        }
        val permanent = repairs.count { it.success }
        val recurring = repairs.size - permanent
        return AtroposMetric(
            id = MetricId.REPAIR_QUALITY,
            value = permanent.toDouble() / repairs.size,
            sampleSize = repairs.size,
            evidenceHashes = evidence.evidenceStore.putAll(
                repairs.filter { !it.success }.map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("$permanent of ${repairs.size} repairs held") },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = "$permanent held, $recurring recurred"
        )
    }

    private fun batchMetrics(evidence: MetricEvidence): List<AtroposMetric> {
        val batches = evidence.of(ObservationKind.BATCH)
        if (batches.isEmpty()) {
            return listOf(
                AtroposMetric.unmeasured(MetricId.BATCH_COMPLETION_RATE, "no batches observed"),
                AtroposMetric.unmeasured(MetricId.ROLLBACK_FREQUENCY, "no batches observed")
            )
        }
        val completed = batches.count { it.success }
        // A batch that neither completed nor rolled back is abandoned. Counting
        // it as a rollback would flatter the rollback figure with work that was
        // never even reverted.
        val rolledBack = batches.count { !it.success && it.detail.contains("rollback", ignoreCase = true) }
        val abandoned = batches.size - completed - rolledBack
        val hashes = evidence.evidenceStore.putAll(
            listOf("batches=${batches.size} completed=$completed rolled_back=$rolledBack abandoned=$abandoned"),
            EvidenceKind.RECEIPT
        )
        return listOf(
            AtroposMetric(
                id = MetricId.BATCH_COMPLETION_RATE,
                value = completed.toDouble() / batches.size,
                sampleSize = batches.size,
                evidenceHashes = hashes,
                detail = "$completed of ${batches.size} completed" +
                    if (abandoned > 0) ", $abandoned abandoned" else ""
            ),
            AtroposMetric(
                id = MetricId.ROLLBACK_FREQUENCY,
                value = rolledBack.toDouble() / batches.size,
                sampleSize = batches.size,
                evidenceHashes = hashes,
                detail = "$rolledBack of ${batches.size} rolled back"
            )
        )
    }

    /**
     * Routes that succeeded on first selection.
     *
     * First selection, not eventual success. A cascade that reaches the fourth
     * provider has worked and has also mis-predicted three times, and the point
     * of this metric is to see the mis-prediction — the fallback chain already
     * guarantees the eventual success.
     */
    private fun routeEffectiveness(evidence: MetricEvidence): AtroposMetric {
        val (firstChoice, total) = evidence.rateOf(ObservationKind.ROUTE_SELECTION)
        if (total == 0) {
            return AtroposMetric.unmeasured(MetricId.ROUTE_EFFECTIVENESS, "no route selections observed")
        }
        return AtroposMetric(
            id = MetricId.ROUTE_EFFECTIVENESS,
            value = firstChoice.toDouble() / total,
            sampleSize = total,
            evidenceHashes = evidence.evidenceStore.putAll(
                evidence.of(ObservationKind.ROUTE_SELECTION).filter { !it.success }
                    .map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("$firstChoice of $total routes succeeded first choice") },
                EvidenceKind.RECEIPT
            ),
            detail = "$firstChoice of $total succeeded on first selection"
        )
    }
}
