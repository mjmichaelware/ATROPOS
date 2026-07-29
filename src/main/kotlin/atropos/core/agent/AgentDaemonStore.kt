package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

class AgentDaemonStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val clock: () -> Instant = { Instant.now() },
    daemonRootOverride: Path? = null,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val daemonRoot = (daemonRootOverride ?: repoRoot.resolve(".atropos/agent/daemon")).normalize()
    private val stateFile = daemonRoot.resolve("state.meta")
    private val lockFile = daemonRoot.resolve("daemon.lock")
    private val stopFile = daemonRoot.resolve("stop.request")
    private val eventsFile = daemonRoot.resolve("events.log")
    private val daemonLogFile = daemonRoot.resolve("daemon.log")

    fun daemonRoot(): Path = daemonRoot
    fun stateFile(): Path = stateFile
    fun lockFile(): Path = lockFile
    fun stopFile(): Path = stopFile
    fun eventsFile(): Path = eventsFile
    fun daemonLogFile(): Path = daemonLogFile

    fun initialRecord(state: AgentDaemonState, pollSeconds: Long, message: String? = null): AgentDaemonRecord {
        val now = clock()
        val pid = ProcessHandle.current().pid()
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("localhost")
        return AgentDaemonRecord(
            state = state,
            instanceId = UUID.randomUUID().toString(),
            owner = "pid-$pid@$host",
            pid = pid,
            host = host,
            startedAt = now,
            updatedAt = now,
            heartbeatAt = now,
            pollSeconds = pollSeconds,
            lastMessage = message,
            metaFile = stateFile
        )
    }

    fun readState(): AgentDaemonRecord? {
        if (!Files.isRegularFile(stateFile)) return null
        val fields = runCatching {
            Files.readAllLines(stateFile, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        return runCatching {
            AgentDaemonRecord(
                state = AgentDaemonState.valueOf(fields["state"].orEmpty()),
                instanceId = fields["instanceId"].orEmpty(),
                owner = fields["owner"].orEmpty(),
                pid = fields["pid"]?.toLongOrNull() ?: -1L,
                host = fields["host"].orEmpty(),
                startedAt = parseInstant(fields["startedAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                heartbeatAt = parseInstant(fields["heartbeatAt"]),
                pollSeconds = fields["pollSeconds"]?.toLongOrNull() ?: AgentDaemonDefaults.DEFAULT_POLL_SECONDS,
                paused = fields["paused"]?.toBooleanStrictOrNull() ?: false,
                lastQueueId = fields["lastQueueId"]?.takeIf { it.isNotBlank() },
                lastJobId = fields["lastJobId"]?.takeIf { it.isNotBlank() },
                lastMessage = decode(fields["lastMessageB64"]).takeIf { it.isNotBlank() },
                stopRequested = fields["stopRequested"]?.toBooleanStrictOrNull() ?: false,
                metaFile = stateFile
            )
        }.getOrNull()
    }

    fun writeState(record: AgentDaemonRecord): AgentDaemonRecord {
        Files.createDirectories(daemonRoot)
        val updated = record.copy(updatedAt = clock(), metaFile = stateFile)
        val tmp = Files.createTempFile(daemonRoot, "state", ".tmp")
        val bytes = render(updated).toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            channel.write(ByteBuffer.wrap(bytes))
            channel.force(true)
        }
        try {
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING)
        }
        appendEvent("state=${updated.state} instance=${updated.instanceId.take(10)} message=${updated.lastMessage ?: ""}")
        return updated
    }

    fun heartbeat(record: AgentDaemonRecord, message: String? = null): AgentDaemonRecord =
        writeState(
            record.copy(
                heartbeatAt = clock(),
                lastMessage = message ?: record.lastMessage,
                stopRequested = stopRequested()
            )
        )

    fun requestStop(reason: String = "operator stop requested") {
        Files.createDirectories(daemonRoot)
        Files.writeString(stopFile, "${clock()}\t$reason\n", StandardCharsets.UTF_8)
        appendEvent("stop requested: $reason")
    }

    fun clearStopRequest() {
        Files.deleteIfExists(stopFile)
    }

    fun stopRequested(): Boolean = Files.isRegularFile(stopFile)

    fun appendEvent(message: String) {
        runCatching {
            Files.createDirectories(daemonRoot)
            Files.writeString(
                eventsFile,
                "${clock()}\t${sanitize(message)}\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
    }

    fun appendDaemonLog(message: String) {
        runCatching {
            Files.createDirectories(daemonRoot)
            Files.writeString(
                daemonLogFile,
                "${clock()}\t${sanitize(message)}\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }
    }

    fun tryLock(): AgentDaemonLock? {
        Files.createDirectories(daemonRoot)
        val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (lock == null) {
            channel.close()
            return null
        }
        return AgentDaemonLock(channel, lock)
    }

    fun validatePollSeconds(value: Long?): Long =
        (value ?: System.getenv("ATROPOS_AGENT_DAEMON_POLL_SECONDS")?.toLongOrNull() ?: AgentDaemonDefaults.DEFAULT_POLL_SECONDS)
            .coerceIn(AgentDaemonDefaults.MIN_POLL_SECONDS, AgentDaemonDefaults.MAX_POLL_SECONDS)

    private fun render(record: AgentDaemonRecord): String = buildString {
        appendLine("state=${record.state}")
        appendLine("instanceId=${record.instanceId}")
        appendLine("owner=${record.owner}")
        appendLine("pid=${record.pid}")
        appendLine("host=${record.host}")
        appendLine("startedAt=${record.startedAt}")
        appendLine("updatedAt=${record.updatedAt}")
        appendLine("heartbeatAt=${record.heartbeatAt ?: ""}")
        appendLine("pollSeconds=${record.pollSeconds}")
        appendLine("paused=${record.paused}")
        appendLine("lastQueueId=${record.lastQueueId ?: ""}")
        appendLine("lastJobId=${record.lastJobId ?: ""}")
        appendLine("lastMessageB64=${encode(record.lastMessage.orEmpty())}")
        appendLine("stopRequested=${record.stopRequested}")
    }

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun sanitize(value: String): String =
        redactionFilter.compact(value, 320)
}

class AgentDaemonLock(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }
}
