package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.LifecycleActionProposals
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

class AgentDaemonService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val repoRoot: Path = AgentDaemonRootResolver.resolve(),
    private val store: AgentDaemonStore = AgentDaemonStore(repoRoot),
    private val queueService: AgentQueueService = AgentQueueService(config),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val sessionSupervisor: ProviderSessionSupervisor = ProviderSessionSupervisor(repoRoot),
    private val processLauncher: AgentDaemonProcessLauncher = AgentDaemonProcessLauncher()
) {
    fun once(activeProviderName: String): AgentDaemonCommandResult {
        enforceDaemonPolicy("once")
        val pollSeconds = store.validatePollSeconds(null)
        val lock = store.tryLock() ?: return AgentDaemonCommandResult(false, "daemon lock is held by another instance", store.readState())
        lock.use {
            store.clearStopRequest()
            sessionSupervisor.createSession(AgentRuntimeKind.OPENCODE, port = 4096)
            var record = store.writeState(store.initialRecord(AgentDaemonState.RUNNING, pollSeconds, "daemon once started"))
            queueService.recover()
            val result = queueService.runNext(activeProviderName)
            for (s in sessionSupervisor.listSessions()) {
                sessionSupervisor.markComplete(s.id)
            }
            record = store.writeState(
                record.copy(
                    state = AgentDaemonState.STOPPED,
                    heartbeatAt = Instant.now(),
                    lastQueueId = result.queueRecord?.id,
                    lastJobId = result.jobRecord?.id,
                    lastMessage = result.message,
                    stopRequested = false
                )
            )
            rememberDaemon(record, "once")
            return AgentDaemonCommandResult(result.ran || result.queueRecord == null, "daemon once: ${result.message}", record)
        }
    }

    fun foreground(activeProviderName: String, pollSecondsOverride: Long? = null): AgentDaemonCommandResult {
        enforceDaemonPolicy("foreground")
        val pollSeconds = store.validatePollSeconds(pollSecondsOverride)
        val lock = store.tryLock() ?: return AgentDaemonCommandResult(false, "daemon lock is held by another instance", store.readState())
        lock.use {
            store.clearStopRequest()
            var record = store.writeState(store.initialRecord(AgentDaemonState.RUNNING, pollSeconds, "foreground daemon started"))
            acquireWakeLockIfRequested()
            try {
                sessionSupervisor.createSession(AgentRuntimeKind.OPENCODE, port = 4096)
                while (!store.stopRequested()) {
                    record = if (record.paused) {
                        store.heartbeat(record.copy(state = AgentDaemonState.PAUSED), "daemon paused")
                    } else {
                        store.appendDaemonLog("queue cycle starting instance=${record.instanceId.take(12)}")
                        queueService.recover()
                        val result = queueService.runNext(activeProviderName)
                        store.appendDaemonLog(
                            "queue cycle finished queue=${result.queueRecord?.id ?: "none"} " +
                                "job=${result.jobRecord?.id ?: "none"} message=${result.message}"
                        )
                        val supervisorSessions = sessionSupervisor.listSessions()
                        for (s in supervisorSessions) {
                            if (s.isStale(java.time.Instant.now())) {
                                sessionSupervisor.recoverStaleSession(s.id)
                                store.appendDaemonLog("recovered stale supervisor session ${s.id}")
                            } else if (s.isLive(java.time.Instant.now())) {
                                sessionSupervisor.heartbeat(s.id, "daemon cycle")
                            }
                        }
                        store.heartbeat(
                            record.copy(
                                state = AgentDaemonState.RUNNING,
                                lastQueueId = result.queueRecord?.id ?: record.lastQueueId,
                                lastJobId = result.jobRecord?.id ?: record.lastJobId,
                                lastMessage = result.message
                            ),
                            result.message
                        )
                    }
                    if (store.stopRequested()) break
                    Thread.sleep(pollSeconds * 1000L)
                }
                for (s in sessionSupervisor.listSessions()) {
                    sessionSupervisor.markComplete(s.id)
                }
                record = store.writeState(
                    record.copy(
                        state = AgentDaemonState.STOPPED,
                        heartbeatAt = Instant.now(),
                        stopRequested = true,
                        lastMessage = "daemon stopped gracefully"
                    )
                )
                store.clearStopRequest()
                rememberDaemon(record, "foreground stopped")
                return AgentDaemonCommandResult(true, "daemon foreground stopped gracefully", record)
            } catch (failure: Exception) {
                val failed = store.writeState(
                    record.copy(
                        state = AgentDaemonState.FAILED,
                        lastMessage = failure.message ?: failure.javaClass.simpleName
                    )
                )
                rememberDaemon(failed, "foreground failed")
                return AgentDaemonCommandResult(false, "daemon foreground failed: ${failure.message ?: failure.javaClass.simpleName}", failed)
            } finally {
                releaseWakeLockIfRequested()
            }
        }
    }

    fun start(): AgentDaemonCommandResult {
        enforceDaemonPolicy("start")
        if (store.tryLock()?.use { false } == null) {
            return AgentDaemonCommandResult(false, "daemon lock is held by another instance", store.readState())
        }
        store.clearStopRequest()
        Files.createDirectories(store.daemonLogFile().parent)
        val jar = repoRoot.resolve("atropos.jar")
        if (!Files.isRegularFile(jar)) {
            return AgentDaemonCommandResult(false, "atropos.jar not found: $jar", store.readState())
        }
        store.appendDaemonLog("daemon start launching bounded java process")
        val process = runCatching {
            processLauncher.launchForeground(repoRoot, jar, store.daemonLogFile())
        }.getOrElse { failure ->
            val message = "daemon launch refused: ${failure.message ?: failure.javaClass.simpleName}"
            store.appendDaemonLog(message)
            return AgentDaemonCommandResult(false, message, store.readState())
        }
        val deadline = System.currentTimeMillis() + 5000L
        while (System.currentTimeMillis() < deadline) {
            val state = store.readState()
            if (state != null && state.state in setOf(AgentDaemonState.RUNNING, AgentDaemonState.PAUSED)) {
                rememberDaemon(state, "started")
                return AgentDaemonCommandResult(true, "daemon started pid=${process.pid()}", state)
            }
            Thread.sleep(250L)
        }
        return AgentDaemonCommandResult(false, "daemon process launched but startup was not verified", store.readState())
    }

    fun stop(): AgentDaemonCommandResult {
        enforceDaemonPolicy("stop")
        store.requestStop()
        store.tryLock()?.use {
            val stopped = store.writeState(
                (store.readState() ?: store.initialRecord(AgentDaemonState.STOPPED, AgentDaemonDefaults.DEFAULT_POLL_SECONDS, "daemon not running"))
                    .copy(
                        state = AgentDaemonState.STOPPED,
                        heartbeatAt = Instant.now(),
                        stopRequested = true,
                        lastMessage = "daemon not running; stop request acknowledged"
                    )
            )
            store.clearStopRequest()
            rememberDaemon(stopped, "stop acknowledged")
            return AgentDaemonCommandResult(true, "daemon was not running; stop acknowledged", stopped)
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(stopWaitSeconds())
        var state = store.readState()
        while (System.nanoTime() < deadline) {
            state = store.readState()
            if (state?.state in setOf(AgentDaemonState.STOPPED, AgentDaemonState.FAILED)) {
                state?.let { rememberDaemon(it, "stopped") }
                return AgentDaemonCommandResult(true, "daemon stopped", state)
            }
            val released = store.tryLock()
            if (released != null) {
                released.close()
                state = store.readState()
                state?.let { rememberDaemon(it, "lock released") }
                return AgentDaemonCommandResult(true, "daemon lock released", state)
            }
            Thread.sleep(500L)
        }
        return AgentDaemonCommandResult(false, "daemon stop requested; still waiting for current work", state)
    }

    fun status(): AgentDaemonCommandResult {
        enforceDaemonPolicy("status")
        val state = store.readState()
        val message = when {
            state == null -> "daemon state: none"
            state.isStale(Instant.now()) -> "daemon state: stale ${state.state}"
            else -> "daemon state: ${state.state}"
        }
        return AgentDaemonCommandResult(true, message, state)
    }

    fun pause(paused: Boolean): AgentDaemonCommandResult {
        enforceDaemonPolicy(if (paused) "pause" else "resume")
        val state = store.readState() ?: return AgentDaemonCommandResult(false, "daemon state not found")
        val next = store.writeState(
            state.copy(
                state = if (paused) AgentDaemonState.PAUSED else AgentDaemonState.RUNNING,
                paused = paused,
                lastMessage = if (paused) "daemon paused" else "daemon resumed"
            )
        )
        rememberDaemon(next, if (paused) "paused" else "resumed")
        return AgentDaemonCommandResult(true, next.lastMessage ?: "pause updated", next)
    }

    private fun acquireWakeLockIfRequested() {
        if (System.getenv("ATROPOS_TERMUX_WAKELOCK") != "1") return
        processLauncher.runWakeTool("termux-wake-lock")
            .onFailure { store.appendDaemonLog("termux wake-lock unavailable: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun releaseWakeLockIfRequested() {
        if (System.getenv("ATROPOS_TERMUX_WAKELOCK") != "1") return
        processLauncher.runWakeTool("termux-wake-unlock")
    }

    private fun stopWaitSeconds(): Long {
        val raw = System.getenv("ATROPOS_AGENT_DAEMON_STOP_WAIT_SECONDS")?.trim()?.toLongOrNull()
        return raw?.coerceIn(1L, 300L) ?: 180L
    }

    /** The lifecycle transition is proposed; the gate decides. */
    private fun enforceDaemonPolicy(operation: String) {
        val decision = agencyGate.evaluate(LifecycleActionProposals.daemon(operation))
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }

    private fun rememberDaemon(record: AgentDaemonRecord, title: String) {
        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.SESSION,
            title = "daemon $title",
            body = buildString {
                appendLine("instance=${record.instanceId}")
                appendLine("state=${record.state}")
                appendLine("poll=${record.pollSeconds}")
                appendLine("queue=${record.lastQueueId ?: "none"}")
                appendLine("job=${record.lastJobId ?: "none"}")
                appendLine("message=${record.lastMessage ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "daemon", record.state.name.lowercase()),
            subjectType = "daemon",
            subjectId = record.instanceId
        )
    }
}
