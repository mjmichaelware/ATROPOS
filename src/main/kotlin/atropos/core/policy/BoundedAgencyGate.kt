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
    private val policyEngine: ExecutionPolicyEngine = ExecutionPolicyEngine(),
    private val territory: atropos.core.territory.TerritoryGrantService =
        atropos.core.territory.TerritoryGrantService()
) {
    fun evaluate(proposal: ActionProposal): AgencyDecision {
        territoryRefusal(proposal)?.let { return it }

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

    /**
     * Territory is checked before policy, because a node reaching outside its
     * grant is refused regardless of how benign the action itself looks.
     *
     * Only dispatched work is bounded this way. [ActionActor.HumanOwner] holds
     * the root grant, so bounding the owner out of their own repository would be
     * incoherent, and [ActionActor.SystemService] lifecycle transitions touch no
     * paths. A hierarchy node that declares no paths has nothing to check —
     * dispatchers refuse those before they get this far.
     */
    private fun territoryRefusal(proposal: ActionProposal): AgencyDecision? {
        val actor = proposal.actor
        if (actor !is ActionActor.HierarchyNode) return null
        if (proposal.targetPaths.isEmpty()) return null

        val offending = territory.firstPathOutsideTerritory(actor, proposal.targetPaths)
            ?: return null

        val reason = "territory refusal: ${actor.identity} holds no grant covering '$offending'"
        territory.recordViolation(actor, offending, reason)

        return AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "territory",
                decision = PolicyDecisionType.DENY,
                actionClass = proposal.actionClass,
                destructive = false,
                reason = reason
            ),
            disposition = AgencyDisposition.POLICY_BLOCKED,
            reason = reason
        )
    }
}
