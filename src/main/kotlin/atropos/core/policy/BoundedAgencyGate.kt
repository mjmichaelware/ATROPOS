package atropos.core.policy

enum class AgencyDisposition {
    ALLOWED,
    POLICY_BLOCKED,
    APPROVAL_REQUIRED
}

data class AgencyDecision(
    val proposal: ActionProposal,
    val policyDecision: ExecutionPolicyDecision,
    val disposition: AgencyDisposition,
    val reason: String
)

class BoundedAgencyGate(
    private val policyEngine: ExecutionPolicyEngine = ExecutionPolicyEngine()
) {
    fun evaluate(proposal: ActionProposal): AgencyDecision {
        val decision = policyEngine.evaluate(proposal.toRequest())
        val disposition = when (decision.decision) {
            PolicyDecisionType.ALLOW -> AgencyDisposition.ALLOWED
            PolicyDecisionType.DENY -> AgencyDisposition.POLICY_BLOCKED
            PolicyDecisionType.APPROVAL_REQUIRED -> AgencyDisposition.APPROVAL_REQUIRED
        }
        return AgencyDecision(
            proposal = proposal,
            policyDecision = decision,
            disposition = disposition,
            reason = decision.reason
        )
    }
}
