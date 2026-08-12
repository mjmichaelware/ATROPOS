/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

data class AntiGamingEvidence(
    val observedDeclaredMetric: Double,
    val outcomeMetric: MetricDeclaration,
    val observedOutcome: Double,
    val evidenceHashes: List<String>
)

data class AntiGamingDecision(
    val passed: Boolean,
    val reason: String
)

/** Requires a claimed metric improvement to correspond to an outcome improvement. */
class AntiGamingAuditor {
    fun audit(proposal: ImprovementProposal, evidence: AntiGamingEvidence): AntiGamingDecision {
        if (!proposal.isComplete()) {
            return AntiGamingDecision(false, "proposal is incomplete: ${proposal.missingFields().joinToString(", ")}")
        }
        if (evidence.evidenceHashes.isEmpty()) {
            return AntiGamingDecision(false, "anti-gaming audit requires independent evidence hashes")
        }
        if (!proposal.metric.improvedBy(evidence.observedDeclaredMetric)) {
            return AntiGamingDecision(false, "declared metric did not improve toward its target")
        }
        if (!evidence.outcomeMetric.isDeclared()) {
            return AntiGamingDecision(false, "outcome metric must declare a distinct baseline and target")
        }
        if (!evidence.outcomeMetric.improvedBy(evidence.observedOutcome)) {
            return AntiGamingDecision(false, "declared metric improved without the measured outcome improving")
        }
        return AntiGamingDecision(
            true,
            "metric and outcome improved with ${evidence.evidenceHashes.size} evidence hash(es)"
        )
    }
}
