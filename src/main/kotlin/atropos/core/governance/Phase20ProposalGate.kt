/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.governance

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import atropos.core.integration.McpEvidenceAndAuthorityPolicy

data class ActionProposal(
    val proposalId: String,
    val authorAgentId: String,
    val rationale: String,
    val targetPaths: List<String>,
    val patchDiff: String
)

data class ProposalEvaluation(
    val isPermitted: Boolean,
    val requiresHumanReview: Boolean,
    val rejectionReason: String?
)

class Phase20ProposalGate(
    val repoRoot: Path,
    val policyEnforcer: McpEvidenceAndAuthorityPolicy = McpEvidenceAndAuthorityPolicy(repoRoot),
    val challengeGate: DecoupledHumanInTheLoopChallengeGate = DecoupledHumanInTheLoopChallengeGate()
) {
    private val approvals = ConcurrentHashMap<String, String>()

    fun evaluateProposal(proposal: ActionProposal): ProposalEvaluation {
        val protectedPaths = setOf(
            "AGENTS.md",
            "SWARM.md",
            "docs/source/",
            ".atropos/ledger",
            ".git"
        )

        val touchesProtected = proposal.targetPaths.any { path ->
            val norm = path.replace('\\', '/').trim().trimStart('/')
            protectedPaths.any { p -> norm == p || norm.startsWith(p) }
        }

        if (touchesProtected) {
            return ProposalEvaluation(
                isPermitted = false,
                requiresHumanReview = true,
                rejectionReason = "Separation-of-Duties: Proposal touches protected governance root. Requires human operator cryptographic approval."
            )
        }

        return ProposalEvaluation(
            isPermitted = true,
            requiresHumanReview = false,
            rejectionReason = null
        )
    }

    fun approveProposal(proposalId: String, operatorSignature: String): Boolean {
        if (operatorSignature.isBlank()) return false
        approvals[proposalId] = operatorSignature
        policyEnforcer.appendToLedger(
            toolName = "proposal_approval",
            callerId = "operator",
            payload = """{"proposalId":"$proposalId","signature":"$operatorSignature"}"""
        )
        return true
    }
}
