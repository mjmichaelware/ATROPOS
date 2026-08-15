/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

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
}
