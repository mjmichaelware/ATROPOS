package atropos.datacenter.managers

/**
 * Typed vote cast by each of the 5 managers.
 */
data class ManagerVote(
    val managerName: String,
    val vote: Decision,
    val confidence: Double,
    val rationale: String
) {
    enum class Decision { APPROVE, REJECT, HOLD }
}

/**
 * 5-Way Manager Quorum Voting Protocol.
 * Aggregates votes from all 5 managers.
 * Guarantees zero records waste the Director's time unless approved by majority quorum
 * with zero compliance or fraud vetoes.
 */
class ManagerQuorumVoter(
    val requiredApprovals: Int = 3
) {
    data class QuorumVerdict(
        val passed: Boolean,
        val approvalCount: Int,
        val rejectionCount: Int,
        val holdCount: Int,
        val summaryRationale: String,
        val votes: List<ManagerVote>
    )

    fun evaluateQuorum(votes: List<ManagerVote>): QuorumVerdict {
        val approvals = votes.count { it.vote == ManagerVote.Decision.APPROVE }
        val rejections = votes.count { it.vote == ManagerVote.Decision.REJECT }
        val holds = votes.count { it.vote == ManagerVote.Decision.HOLD }

        // Strict Hard Veto: If Manager 1 detects fraud or Manager 4 detects consent/rate breach -> Immediate FAIL
        val hasHardVeto = votes.any { 
            it.vote == ManagerVote.Decision.REJECT && 
            (it.managerName.contains("Polarity") || it.managerName.contains("Consent"))
        }

        val passed = !hasHardVeto && approvals >= requiredApprovals && rejections == 0

        val rationale = when {
            hasHardVeto -> "QUORUM VETOED: Critical governance or fraud rejection triggered."
            passed -> "QUORUM PASSED: $approvals approvals achieved with 0 rejections."
            holds > 0 -> "QUORUM HELD: $holds managers requested hold for bundling or quota reset."
            else -> "QUORUM REJECTED: Insufficient approvals ($approvals < $requiredApprovals)."
        }

        return QuorumVerdict(
            passed = passed,
            approvalCount = approvals,
            rejectionCount = rejections,
            holdCount = holds,
            summaryRationale = rationale,
            votes = votes
        )
    }
}
