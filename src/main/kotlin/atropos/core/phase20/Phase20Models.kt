/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

data class Phase20Proposal(
    val id: String,
    val target: String,
    val evidence: List<String>,
    val intent: String
)

sealed class AuditDecision {
    object Approved : AuditDecision()
    data class Rejected(val reason: String) : AuditDecision()
}

data class VersionedAmendment(
    val proposalId: String,
    val version: Int,
    val diff: String,
    val verified: Boolean
)
