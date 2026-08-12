package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.time.Instant

enum class SupervisedSessionState {
    IDLE,
    BUSY,
    FAILED,
    UNAVAILABLE,
    COMPLETE
}

enum class AgentRuntimeKind {
    OPENCODE
}

data class SupervisedSessionRecord(
    val id: String,
    val runtimeKind: AgentRuntimeKind,
    val state: SupervisedSessionState,
    val providerSessionId: String? = null,
    val pid: Long? = null,
    val host: String? = null,
    val port: Int? = null,
    val heartbeatAt: Instant? = null,
    val lastMessage: String? = null,
    val backoffAttempt: Int = 0,
    val nextBackoffAt: Instant? = null,
    val leaseToken: String? = null,
    val leaseExpiresAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metaFile: java.nio.file.Path
) {
    fun isLive(now: Instant): Boolean =
        state == SupervisedSessionState.IDLE || state == SupervisedSessionState.BUSY

    fun isStale(now: Instant, staleSeconds: Long = 60L): Boolean =
        isLive(now) && (heartbeatAt == null || heartbeatAt.plusSeconds(staleSeconds).isBefore(now))

    fun backoffSeconds(maxBackoff: Long = 300L): Long =
        (1L shl backoffAttempt.coerceAtMost(8)).coerceAtMost(maxBackoff)

    fun render(): String = buildString {
        val filter = RedactionFilter()
        appendLine("session id: $id")
        appendLine("runtime: $runtimeKind")
        appendLine("state: $state")
        appendLine("provider session id: ${providerSessionId?.let(filter::redact) ?: "none"}")
        appendLine("pid: ${pid ?: "none"}")
        appendLine("host: ${host ?: "none"}")
        appendLine("port: ${port ?: "none"}")
        appendLine("heartbeat at: ${heartbeatAt ?: "none"}")
        appendLine("last message: ${lastMessage?.let(filter::redact) ?: "none"}")
        appendLine("backoff attempt: $backoffAttempt")
        appendLine("next backoff at: ${nextBackoffAt ?: "none"}")
        appendLine("lease token fingerprint: ${leaseToken?.take(10) ?: "none"}")
        appendLine("lease expires at: ${leaseExpiresAt ?: "none"}")
        appendLine("created at: $createdAt")
        appendLine("updated at: $updatedAt")
        appendLine("record file: $metaFile")
    }.trimEnd()
}

data class SupervisedSessionHealth(
    val sessionId: String,
    val state: SupervisedSessionState,
    val alive: Boolean,
    val message: String
)

data class SupervisedSessionCommandResult(
    val ok: Boolean,
    val message: String,
    val record: SupervisedSessionRecord? = null
)
