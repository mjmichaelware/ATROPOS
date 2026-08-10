package atropos.core.agent

import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ProviderActionProposals

internal class AgentPolicyEnforcer(
    private val agencyGate: BoundedAgencyGate
) {
    fun enforce(provider: String, prompt: String, operation: String) {
        val decision = agencyGate.evaluate(
            ProviderActionProposals.forCall(provider, operation, prompt.length, ActionActor.HumanOwner)
        )
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }
}
