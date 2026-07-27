package atropos.core.agent

import java.nio.file.Path
import java.time.Instant

enum class AgentDaemonState {
    STOPPED,
    STARTING,
    RUNNING,
    PAUSED,
    STOP_REQUESTED,
    FAILED
}

data class AgentDaemonRecord(
    val state: AgentDaemonState,
    val instanceId: String,
    val owner: String,
    val pid: Long,
    val host: String,
    val startedAt: Instant,
    val updatedAt: Instant,
    val heartbeatAt: Instant?,
    val pollSeconds: Long,
    val paused: Boolean = false,
    val lastQueueId: String? = null,
    val lastJobId: String? = null,
    val lastMessage: String? = null,
    val stopRequested: Boolean = false,
    val metaFile: Path
) {
    fun isStale(now: Instant): Boolean =
        state in setOf(AgentDaemonState.RUNNING, AgentDaemonState.PAUSED, AgentDaemonState.STARTING) &&
            heartbeatAt?.plusSeconds((pollSeconds * 3).coerceAtLeast(30))?.isBefore(now) == true

    fun render(): String = buildString {
        appendLine("daemon state: $state")
        appendLine("instance id: $instanceId")
        appendLine("owner: $owner")
        appendLine("pid: $pid")
        appendLine("host: $host")
        appendLine("started at: $startedAt")
        appendLine("updated at: $updatedAt")
        appendLine("heartbeat at: ${heartbeatAt ?: "none"}")
        appendLine("poll seconds: $pollSeconds")
        appendLine("paused: $paused")
        appendLine("stop requested: $stopRequested")
        appendLine("last queue id: ${lastQueueId ?: "none"}")
        appendLine("last job id: ${lastJobId ?: "none"}")
        appendLine("last message: ${lastMessage ?: "none"}")
        appendLine("record file: $metaFile")
    }.trimEnd()
}

data class AgentDaemonCommandResult(
    val ok: Boolean,
    val message: String,
    val record: AgentDaemonRecord? = null
) {
    fun render(): String = buildString {
        appendLine(message)
        record?.let { appendLine(it.render()) }
    }.trimEnd()
}

object AgentDaemonDefaults {
    const val DEFAULT_POLL_SECONDS: Long = 15
    const val MIN_POLL_SECONDS: Long = 2
    const val MAX_POLL_SECONDS: Long = 300
}
