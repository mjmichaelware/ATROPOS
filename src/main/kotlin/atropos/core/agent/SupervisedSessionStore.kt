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

class SupervisedSessionStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val sessionRoot = repoRoot.resolve(".atropos/bootstrap").normalize()
    private val sessionsDir = sessionRoot.resolve("sessions")
    private val lockFile = sessionRoot.resolve("supervisor.lock")

    fun sessionsDir(): Path = sessionsDir
    fun lockFile(): Path = lockFile

    fun initialRecord(runtimeKind: AgentRuntimeKind, port: Int? = null): SupervisedSessionRecord {
        val now = clock()
        val pid = ProcessHandle.current().pid()
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("localhost")
        return SupervisedSessionRecord(
            id = "sess-" + UUID.randomUUID().toString().take(12),
            runtimeKind = runtimeKind,
            state = SupervisedSessionState.IDLE,
            pid = pid,
            host = host,
            port = port,
            createdAt = now,
            updatedAt = now,
            metaFile = sessionsDir.resolve("sessions.meta")
        )
    }

    fun listSessions(): List<SupervisedSessionRecord> {
        if (!Files.isDirectory(sessionsDir)) return emptyList()
        val files = Files.list(sessionsDir).use { stream -> stream.toList() }
        return files
            .filter { it.fileName.toString().endsWith(".meta") && it.fileName.toString().startsWith("sess-") }
            .mapNotNull { readSession(it) }
            .sortedByDescending { it.createdAt }
    }

    fun readSession(sessionId: String): SupervisedSessionRecord? {
        val file = resolveMetaFile(sessionId) ?: return null
        return readSession(file)
    }

    fun writeSession(record: SupervisedSessionRecord): SupervisedSessionRecord {
        Files.createDirectories(sessionsDir)
        val file = sessionsDir.resolve("${record.id}.meta")
        val updated = record.copy(updatedAt = clock(), metaFile = file)
        val tmp = Files.createTempFile(sessionsDir, record.id, ".tmp")
        val bytes = render(updated).toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            channel.write(ByteBuffer.wrap(bytes))
            channel.force(true)
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
        return updated
    }

    fun heartbeat(record: SupervisedSessionRecord, message: String? = null): SupervisedSessionRecord {
        val now = clock()
        return writeSession(
            record.copy(
                heartbeatAt = now,
                lastMessage = message ?: record.lastMessage,
                updatedAt = now
            )
        )
    }

    fun tryLock(): SupervisedSessionLock? {
        Files.createDirectories(sessionRoot)
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
        return SupervisedSessionLock(channel, lock)
    }

    fun deleteSession(sessionId: String) {
        val file = resolveMetaFile(sessionId) ?: return
        Files.deleteIfExists(file)
    }

    private fun resolveMetaFile(sessionId: String): Path? {
        val id = sessionId.trim().removeSuffix(".meta")
        if (id.isBlank() || id.contains("/") || id.contains("\\")) return null
        val file = sessionsDir.resolve("$id.meta").normalize()
        if (!file.startsWith(sessionsDir) || !Files.isRegularFile(file)) return null
        return file
    }

    private fun readSession(file: Path): SupervisedSessionRecord? {
        val fields = runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        return runCatching {
            SupervisedSessionRecord(
                id = fields["id"].orEmpty(),
                runtimeKind = AgentRuntimeKind.valueOf(fields["runtimeKind"].orEmpty()),
                state = SupervisedSessionState.valueOf(fields["state"].orEmpty()),
                providerSessionId = fields["providerSessionId"]?.takeIf { it.isNotBlank() },
                pid = fields["pid"]?.toLongOrNull(),
                host = fields["host"]?.takeIf { it.isNotBlank() },
                port = fields["port"]?.toIntOrNull(),
                heartbeatAt = parseInstant(fields["heartbeatAt"]),
                lastMessage = decode(fields["lastMessageB64"]).takeIf { it.isNotBlank() },
                backoffAttempt = fields["backoffAttempt"]?.toIntOrNull() ?: 0,
                nextBackoffAt = parseInstant(fields["nextBackoffAt"]),
                leaseToken = fields["leaseTokenSha256"]?.takeIf { it.isNotBlank() }
                    ?: fields["leaseToken"]?.takeIf { it.isNotBlank() }?.let(LeaseTokenDigest::of),
                leaseExpiresAt = parseInstant(fields["leaseExpiresAt"]),
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                metaFile = file
            )
        }.getOrNull()
    }

    private fun render(record: SupervisedSessionRecord): String = buildString {
        appendLine("id=${record.id}")
        appendLine("runtimeKind=${record.runtimeKind}")
        appendLine("state=${record.state}")
        appendLine("providerSessionId=${record.providerSessionId ?: ""}")
        appendLine("pid=${record.pid ?: ""}")
        appendLine("host=${record.host ?: ""}")
        appendLine("port=${record.port ?: ""}")
        appendLine("heartbeatAt=${record.heartbeatAt ?: ""}")
        appendLine("lastMessageB64=${encode(redactionFilter.redact(record.lastMessage.orEmpty()))}")
        appendLine("backoffAttempt=${record.backoffAttempt}")
        appendLine("nextBackoffAt=${record.nextBackoffAt ?: ""}")
        appendLine("leaseTokenSha256=${record.leaseToken?.takeIf { it.isNotBlank() }?.let(LeaseTokenDigest::of).orEmpty()}")
        appendLine("leaseExpiresAt=${record.leaseExpiresAt ?: ""}")
        appendLine("createdAt=${record.createdAt}")
        appendLine("updatedAt=${record.updatedAt}")
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
}

class SupervisedSessionLock(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }
}
