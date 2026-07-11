package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import java.util.Comparator

data class AgentDaemonDoctorCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class AgentDaemonDoctorResult(
    val checks: List<AgentDaemonDoctorCheck>,
    val cleanup: Boolean
) {
    val passed: Boolean get() = checks.all { it.passed } && cleanup

    fun render(): String = buildString {
        checks.forEach { check ->
            appendLine("${check.name}: ${if (check.passed) "PASS" else "FAIL"} - ${check.detail}")
        }
        appendLine("cleanup: ${if (cleanup) "PASS" else "FAIL"}")
        appendLine("daemon doctor: ${if (passed) "PASS" else "FAIL"}")
    }.trimEnd()
}

class AgentDaemonDoctor {
    fun run(): AgentDaemonDoctorResult {
        val checks = mutableListOf<AgentDaemonDoctorCheck>()
        val fixture = Files.createTempDirectory("atropos-daemon-doctor-")
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val store = AgentDaemonStore(
            repoRoot = fixture,
            clock = { now },
            daemonRootOverride = fixture.resolve("daemon")
        )

        try {
            val firstLock = store.tryLock()
            checks += check("first lock succeeds", firstLock != null, store.lockFile().toString())
            val secondLock = store.tryLock()
            checks += check("second lock refuses", secondLock == null, "same-process lock contention")

            val initial = store.initialRecord(AgentDaemonState.RUNNING, 15, "doctor")
            val written = store.writeState(initial)
            checks += check("state round-trip", store.readState()?.instanceId == written.instanceId, written.instanceId)

            val rewritten = store.writeState(written.copy(lastMessage = "rewritten"))
            checks += check("atomic rewrite", store.readState()?.lastMessage == "rewritten", rewritten.instanceId)

            now = now.plusSeconds(5)
            val heartbeat = store.heartbeat(rewritten, "heartbeat")
            checks += check("heartbeat", store.readState()?.heartbeatAt == heartbeat.heartbeatAt, heartbeat.heartbeatAt.toString())

            now = now.plusSeconds(60)
            checks += check("stale detection", store.readState()?.isStale(now) == true, now.toString())

            store.requestStop("doctor stop")
            checks += check("stop request", store.stopRequested(), store.stopFile().toString())

            val paused = store.writeState(heartbeat.copy(state = AgentDaemonState.PAUSED, paused = true))
            checks += check("pause", store.readState()?.paused == true && paused.state == AgentDaemonState.PAUSED, paused.instanceId)

            checks += check("poll validation", store.validatePollSeconds(1) == 2L && store.validatePollSeconds(999) == 300L, "bounds 2..300")

            val stopped = store.writeState(paused.copy(state = AgentDaemonState.STOPPED, paused = false, stopRequested = true))
            checks += check("graceful exit", store.readState()?.state == AgentDaemonState.STOPPED, stopped.instanceId)

            firstLock?.close()
        } catch (failure: Exception) {
            checks += AgentDaemonDoctorCheck("doctor exception", false, failure.message ?: failure.javaClass.simpleName)
        }

        val cleanup = runCatching {
            Files.walk(fixture).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }.isSuccess && !Files.exists(fixture)
        return AgentDaemonDoctorResult(checks, cleanup)
    }

    private fun check(name: String, passed: Boolean, detail: String): AgentDaemonDoctorCheck =
        AgentDaemonDoctorCheck(name, passed, detail.replace(Regex("\\s+"), " ").take(240))
}
