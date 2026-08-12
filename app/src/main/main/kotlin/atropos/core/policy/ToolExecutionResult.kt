package atropos.core.policy

data class ToolExecutionResult(
    val proposalId: String,
    val disposition: AgencyDisposition,
    val policyDecision: ExecutionPolicyDecision,
    val authorized: Boolean,
    val executed: Boolean,
    val output: String? = null,
    val refusalReason: String? = null
)
