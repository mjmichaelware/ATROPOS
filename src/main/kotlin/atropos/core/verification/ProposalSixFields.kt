/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.MetricDeclaration
import java.time.Instant

data class Proposal(
    val proposalId: String,
    val baselineHash: String?,
    val targetHash: String?,
    val guardrails: List<String>?,
    val territory: List<String>?,
    val riskMetrics: Map<String, Double>?,
    val rollbackPlan: String?
)

object ProposalSixFields {
    fun validate(prop: Proposal): Boolean {
        return !prop.baselineHash.isNullOrBlank() &&
               !prop.targetHash.isNullOrBlank() &&
               !prop.guardrails.isNullOrEmpty() &&
               !prop.territory.isNullOrEmpty() &&
               (prop.riskMetrics != null) &&
               !prop.rollbackPlan.isNullOrBlank()
    }

    /** Converts the retired shape into the canonical Phase 20 proposal. */
    fun toCanonical(prop: Proposal, proposedBy: String, now: Instant): ImprovementProposal? {
        if (!validate(prop)) return null
        return ImprovementProposal(
            id = prop.proposalId,
            proposedBy = proposedBy,
            summary = "legacy proposal ${prop.proposalId}",
            necessity = listOf(prop.baselineHash!!, prop.targetHash!!),
            baseline = prop.baselineHash!!,
            target = prop.targetHash!!,
            guardrails = prop.guardrails!!,
            territory = prop.territory!!,
            risk = prop.riskMetrics!!.entries.joinToString(",") { "${it.key}=${it.value}" },
            rollback = prop.rollbackPlan!!,
            metric = MetricDeclaration("legacy-proposal", 0.0, 1.0, lowerIsBetter = false),
            createdAt = now
        )
    }
}
