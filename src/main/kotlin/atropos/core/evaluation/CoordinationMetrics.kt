/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * What coordination costs, and what deterministic checking saves.
 *
 * > verifier-first catches (issues found before LLM escalation)
 * > coordination efficiency (tokens per verified engineering change)
 *
 * These are the two metrics Source Doc 3 Part B §9 argues the whole hierarchy
 * exists to move. The argument there is that chatty coordination spends a
 * growing share of tokens on agents talking about status rather than doing
 * engineering, and that deterministic checks are cheaper than model escalation.
 * Neither claim is worth anything unstated as a number, which is what these are.
 *
 * Coordination efficiency is lower-is-better and denominated in *verified*
 * changes, not attempted ones. Denominating in attempts would let a run improve
 * its score by producing more unverified work, which is the opposite of the
 * intent.
 */
class CoordinationMetrics : MetricCalculator {

    override val produces = setOf(MetricId.VERIFIER_FIRST_CATCHES, MetricId.COORDINATION_EFFICIENCY)

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> =
        listOf(verifierFirst(evidence), efficiency(evidence))

    /**
     * The share of issues caught deterministically before a model was asked.
     *
     * Denominator is catches plus escalations — every issue that was found at
     * all, by either route. An issue nobody found appears in neither, which is
     * honest: this measures where detection happened, not whether detection was
     * complete. Completeness is what the test suite is for.
     */
    private fun verifierFirst(evidence: MetricEvidence): AtroposMetric {
        val catches = evidence.of(ObservationKind.VERIFIER_CATCH)
        val escalations = evidence.of(ObservationKind.MODEL_ESCALATION)
        val total = catches.size + escalations.size
        if (total == 0) {
            return AtroposMetric.unmeasured(
                MetricId.VERIFIER_FIRST_CATCHES,
                "no issues were found by either route; nothing to attribute"
            )
        }
        return AtroposMetric(
            id = MetricId.VERIFIER_FIRST_CATCHES,
            value = catches.size.toDouble() / total,
            sampleSize = total,
            evidenceHashes = evidence.evidenceStore.putAll(
                (catches + escalations).map { it.rawEvidence.ifBlank { it.detail } },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = "${catches.size} caught deterministically, ${escalations.size} escalated to a model"
        )
    }

    /**
     * Tokens per verified change.
     *
     * Unmeasured rather than infinite when nothing was verified. A run that
     * spent tokens and verified nothing has no efficiency — it has a failure,
     * and reporting that as a very large number invites it to be averaged away
     * with runs that succeeded.
     */
    private fun efficiency(evidence: MetricEvidence): AtroposMetric {
        val spend = evidence.of(ObservationKind.TOKEN_SPEND)
        val verified = evidence.of(ObservationKind.VERIFIED_CHANGE).count { it.success }
        if (spend.isEmpty()) {
            return AtroposMetric.unmeasured(MetricId.COORDINATION_EFFICIENCY, "no token spend recorded")
        }
        if (verified == 0) {
            return AtroposMetric.unmeasured(
                MetricId.COORDINATION_EFFICIENCY,
                "tokens were spent but nothing was verified; that is a failure, not an efficiency"
            )
        }
        val tokens = spend.sumOf { it.value }
        return AtroposMetric(
            id = MetricId.COORDINATION_EFFICIENCY,
            value = tokens / verified,
            sampleSize = verified,
            evidenceHashes = evidence.evidenceStore.putAll(
                spend.map { it.rawEvidence.ifBlank { it.detail } },
                EvidenceKind.RECEIPT
            ),
            detail = "%.0f tokens across %d verified change(s)".format(tokens, verified)
        )
    }
}
