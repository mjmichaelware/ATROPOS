/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * Restart recovery, Source Doc 3 §4.1's first named metric.
 *
 * > restart recovery success rate
 *
 * Measures the proportion of restarts that restored the exact claim, territory,
 * evidence pointers and next action — the Blueprint's recovery proof, expressed
 * as a rate rather than a single demonstration. One successful recovery proves
 * the path exists; a rate over many says whether it can be relied on, which is
 * the thing long-horizon autonomy actually depends on.
 */
class RecoveryMetrics : MetricCalculator {

    override val produces = setOf(MetricId.RESTART_RECOVERY_SUCCESS)

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> {
        val restarts = evidence.of(ObservationKind.RESTART)
        if (restarts.isEmpty()) {
            return listOf(
                AtroposMetric.unmeasured(
                    MetricId.RESTART_RECOVERY_SUCCESS,
                    "no restart observations recorded"
                )
            )
        }

        val recovered = restarts.count { it.success }
        val hashes = evidence.evidenceStore.putAll(
            restarts.map { it.rawEvidence.ifBlank { it.detail } },
            EvidenceKind.RECEIPT
        )
        return listOf(
            AtroposMetric(
                id = MetricId.RESTART_RECOVERY_SUCCESS,
                value = recovered.toDouble() / restarts.size,
                sampleSize = restarts.size,
                evidenceHashes = hashes,
                detail = "$recovered of ${restarts.size} restarts restored exact state"
            )
        )
    }
}
