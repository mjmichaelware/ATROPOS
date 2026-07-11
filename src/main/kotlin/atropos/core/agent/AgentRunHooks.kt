package atropos.core.agent

class AgentRunCancelledException(message: String) : RuntimeException(message)

class AgentRunHooks(
    private val checkpointHandler: (AgentQueueCheckpoint, AgentJobRecord?, String) -> Unit = { _, _, _ -> },
    private val beforeStageHandler: (AgentQueueCheckpoint, AgentJobRecord?) -> Unit = { _, _ -> }
) {
    fun beforeStage(checkpoint: AgentQueueCheckpoint, job: AgentJobRecord? = null) {
        beforeStageHandler(checkpoint, job)
    }

    fun checkpoint(checkpoint: AgentQueueCheckpoint, job: AgentJobRecord? = null, message: String = checkpoint.name.lowercase()) {
        checkpointHandler(checkpoint, job, message)
    }

    companion object {
        val NONE = AgentRunHooks()
    }
}
