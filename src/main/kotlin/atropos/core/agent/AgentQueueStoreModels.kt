package atropos.core.agent

data class AgentQueueEventResult(
    val appended: Boolean,
    val failure: String? = null
)

data class AgentQueueLeaseResult(
    val record: AgentQueueRecord? = null,
    val refusalReason: String? = null
)
