package atropos.cli.commands

data class AgentCommandExecutionResult(
    val outcome: AgentCommandOutcome,
    val lastKnownPatchId: String? = null
)
