package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.net.InetAddress
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class AgentQueueStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    queueRootOverride: Path? = null,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val queueRoot = (queueRootOverride ?: repoRoot.resolve(".atropos/agent/queue")).normalize()
    private val entriesDir = queueRoot.resolve("entries").normalize()
    private val eventsDir = queueRoot.resolve("events").normalize()
    private val locksDir = queueRoot.resolve("locks").normalize()
    private val doctorDir = queueRoot.resolve("doctor").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneId.systemDefault())
    private val codec = AgentQueueRecordCodec(entriesDir, clock, redactionFilter)

    fun queueRoot(): Path = queueRoot
    fun entriesDirectory(): Path = entriesDir
    fun eventsDirectory(): Path = eventsDir
    fun locksDirectory(): Path = locksDir
    fun doctorDirectory(): Path = doctorDir

    fun createEntry(
        task: String,
        smokeCommand: String?,
        state: AgentQueueState = AgentQueueState.QUEUED,
        checkpoint: AgentQueueCheckpoint = AgentQueueCheckpoint.QUEUED,
        provider: String? = null,
        failureReason: String? = null
    ): AgentQueueRecord {
        Files.createDirectories(entriesDir)
        val now = clock()
        val id = nextQueueId(now)
        val record = codec.sanitize(
            AgentQueueRecord(
                id = id,
                task = task.trim(),
                smokeCommand = smokeCommand?.trim()?.takeIf { it.isNotBlank() },
                state = state,
                checkpoint = checkpoint,
                provider = provider,
                failureReason = failureReason,
                createdAt = now,
                updatedAt = now,
                finishedAt = if (state.terminal) now else null,
                metaFile = entriesDir.resolve("$id.meta")
            )
        )
        codec.write(record)
        appendEvent(record, "created", null, state, "queue entry created")
        return record
    }

    fun update(
        record: AgentQueueRecord,
        eventType: String = "updated",
        previousState: AgentQueueState? = null,
        message: String = eventType
    ): AgentQueueRecord {
        if (previousState != null) {
            AgentQueueTransitions.requireTransition(previousState, record.state)
        }
        val updated = codec.sanitize(record.copy(updatedAt = clock()))
        codec.write(updated)
        appendEvent(updated, eventType, previousState, updated.state, message)
        return updated
    }

    fun resolve(reference: String): AgentQueueRecord? {
        val id = resolveQueueId(reference) ?: return null
        val metaFile = entriesDir.resolve("$id.meta").normalize()
        if (!metaFile.startsWith(entriesDir) || !Files.isRegularFile(metaFile)) return null
        return codec.parse(metaFile)
    }

    fun latest(): AgentQueueRecord? =
        listEntries(limit = 1).firstOrNull()

    fun listEntries(limit: Int = 20): List<AgentQueueRecord> {
        if (!Files.isDirectory(entriesDir)) return emptyList()
        return try {
            Files.list(entriesDir).use { stream ->
                stream
                    .map { it.normalize() }
                    .filter { it.fileName.toString().startsWith("queue-") && it.fileName.toString().endsWith(".meta") }
                    .toList()
                    .map { codec.parse(it) }
                    .sortedWith(
                        compareByDescending<AgentQueueRecord> { it.updatedAt }
                            .thenByDescending { it.createdAt }
                            .thenByDescending { it.id }
                    )
                    .take(limit.coerceAtLeast(0))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun allEntries(): List<AgentQueueRecord> = listEntries(Int.MAX_VALUE)

    fun appendEvent(
        record: AgentQueueRecord,
        eventType: String,
        previousState: AgentQueueState?,
        newState: AgentQueueState,
        message: String
    ): AgentQueueEventResult {
        return try {
            Files.createDirectories(eventsDir)
            val line = buildString {
                append("timestamp=").append(clock())
                append('\t').append("queueId=").append(record.id)
                append('\t').append("event=").append(safeAtom(eventType))
                append('\t').append("previous=").append(previousState ?: "none")
                append('\t').append("new=").append(newState)
                append('\t').append("checkpoint=").append(record.checkpoint)
                append('\t').append("lease=").append(record.lease?.fingerprint() ?: "none")
                append('\t').append("attempts=").append(record.attempts)
                append('\t').append("message=").append(sanitizeEventMessage(message))
                append('\n')
            }
            Files.writeString(
                eventsDir.resolve("${record.id}.events"),
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
            AgentQueueEventResult(appended = true)
        } catch (failure: Exception) {
            AgentQueueEventResult(appended = false, failure = failure.message ?: "event append failed")
        }
    }

    fun <T> withSelectionLock(body: () -> T): T? {
        Files.createDirectories(locksDir)
        val lockFile = locksDir.resolve("queue.lock")
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) return null
            lock.use {
                return body()
            }
        }
    }

    fun acquireLease(recordId: String, owner: String, leaseSeconds: Long): AgentQueueLeaseResult {
        val now = clock()
        val record = resolve(recordId) ?: return AgentQueueLeaseResult(refusalReason = "queue entry not found: $recordId")
        if (record.state.terminal) {
            return AgentQueueLeaseResult(refusalReason = "queue entry is terminal: ${record.state}")
        }
        val liveLease = record.lease?.takeIf { it.isLive(now) }
        if (liveLease != null && record.state in setOf(AgentQueueState.LEASED, AgentQueueState.RUNNING)) {
            return AgentQueueLeaseResult(refusalReason = "queue entry has a live lease owned by ${liveLease.owner}")
        }
        if (!AgentQueueTransitions.isSelectable(record, now)) {
            return AgentQueueLeaseResult(refusalReason = "queue entry is not eligible: ${record.state}")
        }
        if (record.attempts >= record.maxAttempts) {
            val failed = update(
                record.copy(
                    state = AgentQueueState.FAILED,
                    lease = null,
                    failureReason = "maximum attempts exhausted",
                    finishedAt = now
                ),
                eventType = "retry_exhausted",
                previousState = record.state,
                message = "maximum attempts exhausted"
            )
            return AgentQueueLeaseResult(record = failed, refusalReason = "maximum attempts exhausted")
        }

        val lease = AgentQueueLease(
            token = UUID.randomUUID().toString(),
            owner = owner,
            acquiredAt = now,
            heartbeatAt = now,
            expiresAt = now.plusSeconds(leaseSeconds)
        )
        val leased = update(
            record.copy(
                state = AgentQueueState.LEASED,
                checkpoint = AgentQueueCheckpoint.CLAIMED,
                attempts = record.attempts + 1,
                lease = lease,
                lastAttemptAt = now,
                nextEligibleAt = null
            ),
            eventType = "lease_acquired",
            previousState = record.state,
            message = "lease acquired by ${lease.owner}"
        )
        return AgentQueueLeaseResult(record = leased)
    }

    fun markRunning(record: AgentQueueRecord): AgentQueueRecord {
        val current = resolve(record.id) ?: record
        ensureLeaseOwner(current, record.lease?.token)
        return update(
            current.copy(
                state = AgentQueueState.RUNNING,
                lease = record.lease ?: current.lease
            ),
            eventType = "running",
            previousState = current.state,
            message = "execution started"
        )
    }

    fun heartbeat(
        recordId: String,
        leaseToken: String?,
        checkpoint: AgentQueueCheckpoint? = null,
        leaseSeconds: Long
    ): AgentQueueRecord? {
        val current = resolve(recordId) ?: return null
        ensureLeaseOwner(current, leaseToken)
        val now = clock()
        val lease = current.lease?.copy(
            heartbeatAt = now,
            expiresAt = now.plusSeconds(leaseSeconds)
        )
        return update(
            current.copy(
                checkpoint = checkpoint ?: current.checkpoint,
                lease = lease
            ),
            eventType = "heartbeat",
            previousState = current.state,
            message = "checkpoint ${checkpoint ?: current.checkpoint}"
        )
    }

    fun cancel(record: AgentQueueRecord, reason: String): AgentQueueRecord {
        val current = resolve(record.id) ?: record
        val now = clock()
        return when (current.state) {
            AgentQueueState.QUEUED,
            AgentQueueState.RETRY_WAIT -> update(
                current.copy(
                    state = AgentQueueState.CANCELLED,
                    cancellationRequested = true,
                    cancellationReason = reason,
                    cancelledAt = now,
                    finishedAt = now,
                    lease = null
                ),
                eventType = "cancelled",
                previousState = current.state,
                message = reason
            )
            AgentQueueState.LEASED,
            AgentQueueState.RUNNING -> update(
                current.copy(
                    cancellationRequested = true,
                    cancellationReason = reason
                ),
                eventType = "cancellation_requested",
                previousState = current.state,
                message = reason
            )
            else -> {
                appendEvent(current, "cancel_noop", current.state, current.state, "terminal entry not rewritten")
                current
            }
        }
    }

    fun ownerId(): String {
        val pid = ProcessHandle.current().pid()
        val host = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("localhost")
        return "pid-$pid@$host-${UUID.randomUUID()}"
    }

    private fun ensureLeaseOwner(record: AgentQueueRecord, token: String?) {
        val expected = record.lease?.token
        require(expected == null || expected == token) { "queue entry lease is owned by another worker" }
    }

    private fun resolveQueueId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return latest()?.id
        val cleaned = trimmed.removeSuffix(".meta").trim()
        if (cleaned.isBlank() || cleaned.contains('/') || cleaned.contains('\\')) return null
        return cleaned
    }

    private fun nextQueueId(createdAt: Instant): String {
        val timestamp = formatter.format(createdAt)
        var candidate = "queue-$timestamp-${UUID.randomUUID().toString().take(8)}"
        while (Files.exists(entriesDir.resolve("$candidate.meta"))) {
            candidate = "queue-$timestamp-${UUID.randomUUID().toString().take(8)}"
        }
        return candidate
    }

    private fun safeAtom(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "event" }

    private fun sanitizeEventMessage(value: String): String {
        return redactionFilter.compact(value, 240)
    }
}
