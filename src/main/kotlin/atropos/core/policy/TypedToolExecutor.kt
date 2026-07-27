package atropos.core.policy

class TypedToolExecutor(
    private val gate: BoundedAgencyGate = BoundedAgencyGate()
) {
    fun execute(
        proposal: ActionProposal,
        executor: (() -> String)? = null
    ): ToolExecutionResult {
        val decision = gate.evaluate(proposal)
        return when (decision.disposition) {
            AgencyDisposition.POLICY_BLOCKED -> ToolExecutionResult(
                proposalId = proposal.id,
                disposition = decision.disposition,
                policyDecision = decision.policyDecision,
                authorized = false,
                executed = false,
                refusalReason = decision.reason
            )

            AgencyDisposition.APPROVAL_REQUIRED -> ToolExecutionResult(
                proposalId = proposal.id,
                disposition = decision.disposition,
                policyDecision = decision.policyDecision,
                authorized = false,
                executed = false,
                refusalReason = decision.reason
            )

            AgencyDisposition.ALLOWED -> {
                if (executor == null) {
                    ToolExecutionResult(
                        proposalId = proposal.id,
                        disposition = decision.disposition,
                        policyDecision = decision.policyDecision,
                        authorized = true,
                        executed = false,
                        refusalReason = "no typed executor bound for ${proposal.actionClass.name.lowercase()}"
                    )
                } else {
                    ToolExecutionResult(
                        proposalId = proposal.id,
                        disposition = decision.disposition,
                        policyDecision = decision.policyDecision,
                        authorized = true,
                        executed = true,
                        output = executor()
                    )
                }
            }
        }
    }
}
