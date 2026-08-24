package atropos.core.policy

class TypedToolExecutor(
    private val gate: BoundedAgencyGate = BoundedAgencyGate()
) {
    fun execute(
        proposal: ActionProposal,
        executor: (() -> String)? = null
    ): ToolExecutionResult {
        return execute(gate.evaluate(proposal), executor)
    }

    /**
     * Executes a decision that has already crossed the canonical gate.
     *
     * Inbound adapters such as MCP must first perform their source/territory
     * admission and retain that exact decision. Re-evaluating it here would
     * create a second policy crossing; accepting only the [AgencyDecision]
     * keeps the executor as the single typed execution seam without changing
     * the decision that was audited.
     */
    fun execute(
        decision: AgencyDecision,
        executor: (() -> String)? = null
    ): ToolExecutionResult {
        return when (decision.disposition) {
            AgencyDisposition.POLICY_BLOCKED -> ToolExecutionResult(
                proposalId = decision.proposal.id,
                disposition = decision.disposition,
                policyDecision = decision.policyDecision,
                authorized = false,
                executed = false,
                refusalReason = decision.reason
            )

            AgencyDisposition.APPROVAL_REQUIRED -> ToolExecutionResult(
                proposalId = decision.proposal.id,
                disposition = decision.disposition,
                policyDecision = decision.policyDecision,
                authorized = false,
                executed = false,
                refusalReason = decision.reason
            )

            AgencyDisposition.ALLOWED -> {
                if (executor == null) {
                    ToolExecutionResult(
                        proposalId = decision.proposal.id,
                        disposition = decision.disposition,
                        policyDecision = decision.policyDecision,
                        authorized = true,
                        executed = false,
                        refusalReason = "no typed executor bound for ${decision.proposal.actionClass.name.lowercase()}"
                    )
                } else {
                    ToolExecutionResult(
                        proposalId = decision.proposal.id,
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
