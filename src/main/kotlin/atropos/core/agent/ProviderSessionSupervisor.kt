package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class ProviderSessionSupervisor(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val store: SupervisedSessionStore = SupervisedSessionStore(repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() },
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
) {
    private val maxBackoffSeconds = 300L

    fun createSession(runtimeKind: AgentRuntimeKind, port: Int? = null): SupervisedSessionCommandResult {
        val lock = store.tryLock() ?: return SupervisedSessionCommandResult(false, "supervisor lock is held by another instance")
        lock.use {
            val existing = store.listSessions().firstOrNull { it.runtimeKind == runtimeKind }
            if (existing != null && existing.isLive(clock())) {
                return SupervisedSessionCommandResult(true, "session already exists: ${existing.id}", existing)
            }
            if (existing != null) {
                store.deleteSession(existing.id)
            }
            val record = store.initialRecord(runtimeKind, port)
            val written = store.writeSession(record)
            rememberSupervisor(written, "created")
            return SupervisedSessionCommandResult(true, "session created: ${written.id}", written)
        }
    }

    fun connectSession(sessionId: String, providerSessionId: String): SupervisedSessionCommandResult {
        val record = store.readSession(sessionId)
            ?: return SupervisedSessionCommandResult(false, "session not found: $sessionId")
        if (record.state == SupervisedSessionState.COMPLETE) {
            return SupervisedSessionCommandResult(false, "session already complete: $sessionId")
        }
        val updated = store.writeSession(
            record.copy(
                providerSessionId = providerSessionId,
                state = SupervisedSessionState.IDLE,
                backoffAttempt = 0,
                nextBackoffAt = null
            )
        )
        rememberSupervisor(updated, "connected")
        return SupervisedSessionCommandResult(true, "session connected: $sessionId", updated)
    }

    fun markBusy(sessionId: String): SupervisedSessionCommandResult {
        return transitionState(sessionId, SupervisedSessionState.BUSY, "marked busy")
    }

    fun markFailed(sessionId: String, reason: String): SupervisedSessionCommandResult {
        return transitionState(sessionId, SupervisedSessionState.FAILED, reason)
    }

    fun markComplete(sessionId: String): SupervisedSessionCommandResult {
        return transitionState(sessionId, SupervisedSessionState.COMPLETE, "session complete")
    }

    fun markUnavailable(sessionId: String, reason: String): SupervisedSessionCommandResult {
        val record = store.readSession(sessionId)
            ?: return SupervisedSessionCommandResult(false, "session not found: $sessionId")
        val backoff = record.backoffAttempt + 1
        val delay = record.backoffSeconds(maxBackoffSeconds)
        val nextBackoff = clock().plusSeconds(delay)
        val updated = store.writeSession(
            record.copy(
                state = SupervisedSessionState.UNAVAILABLE,
                backoffAttempt = backoff,
                nextBackoffAt = nextBackoff,
                lastMessage = reason,
                heartbeatAt = clock()
            )
        )
        rememberSupervisor(updated, "unavailable")
        return SupervisedSessionCommandResult(true, "session marked unavailable (backoff $backoff, delay ${delay}s)", updated)
    }

    fun heartbeat(sessionId: String, message: String? = null): SupervisedSessionCommandResult {
        val record = store.readSession(sessionId)
            ?: return SupervisedSessionCommandResult(false, "session not found: $sessionId")
        if (!record.isLive(clock())) {
            return SupervisedSessionCommandResult(false, "session not live: $sessionId state=${record.state}")
        }
        store.heartbeat(record, message)
        return SupervisedSessionCommandResult(true, "heartbeat: $sessionId")
    }

    fun detectDeadSessions(): List<SupervisedSessionRecord> {
        val now = clock()
        return store.listSessions().filter { it.isStale(now) }
    }

    fun recoverStaleSession(sessionId: String): SupervisedSessionCommandResult {
        val record = store.readSession(sessionId)
            ?: return SupervisedSessionCommandResult(false, "session not found: $sessionId")
        val now = clock()
        if (!record.isStale(now)) {
            return SupervisedSessionCommandResult(true, "session is not stale, no recovery needed", record)
        }
        val backoff = record.backoffAttempt + 1
        val delay = record.backoffSeconds(maxBackoffSeconds)
        val updated = store.writeSession(
            record.copy(
                state = SupervisedSessionState.UNAVAILABLE,
                backoffAttempt = backoff,
                nextBackoffAt = now.plusSeconds(delay),
                lastMessage = "recovered from stale state",
                heartbeatAt = now
            )
        )
        rememberSupervisor(updated, "recovered")
        return SupervisedSessionCommandResult(true, "session recovered (backoff $backoff, delay ${delay}s)", updated)
    }

    fun probeRuntime(record: SupervisedSessionRecord): SupervisedSessionHealth {
        val port = record.port ?: return SupervisedSessionHealth(
            record.id, record.state, false, "no port configured for runtime"
        )
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:$port/session/status"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                SupervisedSessionHealth(record.id, record.state, true, "runtime responded: ${response.body().take(80)}")
            } else {
                SupervisedSessionHealth(record.id, record.state, false, "runtime returned status ${response.statusCode()}")
            }
        } catch (e: Exception) {
            SupervisedSessionHealth(record.id, record.state, false, "runtime unreachable: ${e.message}")
        }
    }

    fun listSessions(): List<SupervisedSessionRecord> = store.listSessions()

    fun readSession(sessionId: String): SupervisedSessionRecord? = store.readSession(sessionId)

    fun status(): String = buildString {
        val sessions = store.listSessions()
        if (sessions.isEmpty()) {
            appendLine("no supervised sessions")
            return@buildString
        }
        appendLine("supervised sessions:")
        sessions.forEach { session ->
            val health = probeRuntime(session)
            appendLine("  ${session.id} runtime=${session.runtimeKind} state=${session.state} " +
                "alive=${health.alive} pid=${session.pid ?: "none"} " +
                "backoff=${session.backoffAttempt} msg=${session.lastMessage ?: "none"}")
        }
    }.trimEnd()

    private fun transitionState(sessionId: String, newState: SupervisedSessionState, reason: String): SupervisedSessionCommandResult {
        val record = store.readSession(sessionId)
            ?: return SupervisedSessionCommandResult(false, "session not found: $sessionId")
        val now = clock()
        val updated = store.writeSession(
            record.copy(
                state = newState,
                lastMessage = reason,
                heartbeatAt = now,
                updatedAt = now
            )
        )
        rememberSupervisor(updated, reason)
        return SupervisedSessionCommandResult(true, "session $sessionId state=${newState.name}: $reason", updated)
    }

    private fun rememberSupervisor(record: SupervisedSessionRecord, title: String) {
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.SESSION,
            title = "supervisor $title",
            body = buildString {
                appendLine("session=${record.id}")
                appendLine("runtime=${record.runtimeKind}")
                appendLine("state=${record.state}")
                appendLine("provider=${record.providerSessionId ?: "none"}")
                appendLine("pid=${record.pid ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "supervisor", record.state.name.lowercase()),
            subjectType = "supervised_session",
            subjectId = record.id
        )
    }
}
