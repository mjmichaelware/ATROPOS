package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

data class AgentQueueEventResult(
    val appended: Boolean,
    val failure: String? = null
)

data class AgentQueueLeaseResult(
    val record: AgentQueueRecord? = null,
    val refusalReason: String? = null
)

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
        val record = sanitizeRecord(
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
        writeRecord(record)
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
        val updated = sanitizeRecord(record.copy(updatedAt = clock()))
        writeRecord(updated)
        appendEvent(updated, eventType, previousState, updated.state, message)
        return updated
    }

    fun resolve(reference: String): AgentQueueRecord? {
        val id = resolveQueueId(reference) ?: return null
        val metaFile = entriesDir.resolve("$id.meta").normalize()
        if (!metaFile.startsWith(entriesDir) || !Files.isRegularFile(metaFile)) return null
        return parseRecord(metaFile)
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
                    .map { parseRecord(it) }
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

    private fun writeRecord(record: AgentQueueRecord) {
        Files.createDirectories(entriesDir)
        val tmp = Files.createTempFile(entriesDir, record.id, ".tmp")
        val bytes = renderRecord(record).toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            channel.write(ByteBuffer.wrap(bytes))
            channel.force(true)
        }
        try {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun renderRecord(record: AgentQueueRecord): String = buildString {
        appendLine("id=${record.id}")
        appendLine("taskB64=${encode(record.task)}")
        appendLine("smokeCommandB64=${encode(record.smokeCommand.orEmpty())}")
        appendLine("state=${record.state}")
        appendLine("checkpoint=${record.checkpoint}")
        appendLine("attempts=${record.attempts}")
        appendLine("maxAttempts=${record.maxAttempts}")
        appendLine("jobId=${record.jobId ?: ""}")
        appendLine("provider=${record.provider ?: ""}")
        appendLine("patchId=${record.patchId ?: ""}")
        appendLine("appliedPatchId=${record.appliedPatchId ?: ""}")
        appendLine("verificationId=${record.verificationId ?: ""}")
        appendLine("repairId=${record.repairId ?: ""}")
        appendLine("contextExportPathB64=${encode(record.contextExportPath.orEmpty())}")
        appendLine("finalJobResultB64=${encode(record.finalJobResult.orEmpty())}")
        appendLine("sourceEvidenceB64=${encode(record.sourceEvidence.orEmpty())}")
        appendLine("impactedSymbolsB64=${encode(record.impactedSymbols.joinToString("\n"))}")
        appendLine("failureReasonB64=${encode(record.failureReason.orEmpty())}")
        appendLine("nextEligibleAt=${record.nextEligibleAt ?: ""}")
        appendLine("leaseToken=${record.lease?.token ?: ""}")
        appendLine("leaseOwnerB64=${encode(record.lease?.owner.orEmpty())}")
        appendLine("leaseAcquiredAt=${record.lease?.acquiredAt ?: ""}")
        appendLine("leaseHeartbeatAt=${record.lease?.heartbeatAt ?: ""}")
        appendLine("leaseExpiresAt=${record.lease?.expiresAt ?: ""}")
        appendLine("cancellationRequested=${record.cancellationRequested}")
        appendLine("cancellationReasonB64=${encode(record.cancellationReason.orEmpty())}")
        appendLine("cancelledAt=${record.cancelledAt ?: ""}")
        appendLine("recoveryCount=${record.recoveryCount}")
        appendLine("lastAttemptAt=${record.lastAttemptAt ?: ""}")
        appendLine("createdAt=${record.createdAt}")
        appendLine("updatedAt=${record.updatedAt}")
        appendLine("finishedAt=${record.finishedAt ?: ""}")
        appendLine("corruptReasonB64=${encode(record.corruptReason.orEmpty())}")
        appendLine("metaFile=${record.metaFile.fileName}")
    }

    private fun parseRecord(metaFile: Path): AgentQueueRecord {
        val fields = try {
            Files.readAllLines(metaFile, StandardCharsets.UTF_8)
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    line.substring(0, index) to line.substring(index + 1)
                }
                .toMap()
        } catch (failure: Exception) {
            return corruptRecord(metaFile, "unable to read record: ${failure.message ?: failure.javaClass.simpleName}")
        }

        return try {
            val id = fields["id"]?.takeIf { it.isNotBlank() } ?: metaFile.fileName.toString().removeSuffix(".meta")
            val createdAt = parseInstant(fields["createdAt"]) ?: throw IllegalArgumentException("missing createdAt")
            val state = enumValueOf<AgentQueueState>(fields["state"] ?: throw IllegalArgumentException("missing state"))
            val checkpoint = enumValueOf<AgentQueueCheckpoint>(fields["checkpoint"] ?: AgentQueueCheckpoint.QUEUED.name)
            val lease = parseLease(fields)
            AgentQueueRecord(
                id = id,
                task = decode(fields["taskB64"]),
                smokeCommand = decode(fields["smokeCommandB64"]).takeIf { it.isNotBlank() },
                state = state,
                checkpoint = checkpoint,
                attempts = fields["attempts"]?.toIntOrNull() ?: 0,
                maxAttempts = fields["maxAttempts"]?.toIntOrNull() ?: AgentQueueDefaults.MAX_ATTEMPTS,
                jobId = fields["jobId"]?.takeIf { it.isNotBlank() },
                provider = fields["provider"]?.takeIf { it.isNotBlank() },
                patchId = fields["patchId"]?.takeIf { it.isNotBlank() },
                appliedPatchId = fields["appliedPatchId"]?.takeIf { it.isNotBlank() },
                verificationId = fields["verificationId"]?.takeIf { it.isNotBlank() },
                repairId = fields["repairId"]?.takeIf { it.isNotBlank() },
                contextExportPath = decode(fields["contextExportPathB64"]).takeIf { it.isNotBlank() },
                finalJobResult = decode(fields["finalJobResultB64"]).takeIf { it.isNotBlank() },
                sourceEvidence = decode(fields["sourceEvidenceB64"]).takeIf { it.isNotBlank() },
                impactedSymbols = decode(fields["impactedSymbolsB64"])
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList(),
                failureReason = decode(fields["failureReasonB64"]).takeIf { it.isNotBlank() },
                nextEligibleAt = parseInstant(fields["nextEligibleAt"]),
                lease = lease,
                cancellationRequested = fields["cancellationRequested"]?.toBooleanStrictOrNull() ?: false,
                cancellationReason = decode(fields["cancellationReasonB64"]).takeIf { it.isNotBlank() },
                cancelledAt = parseInstant(fields["cancelledAt"]),
                recoveryCount = fields["recoveryCount"]?.toIntOrNull() ?: 0,
                lastAttemptAt = parseInstant(fields["lastAttemptAt"]),
                createdAt = createdAt,
                updatedAt = parseInstant(fields["updatedAt"]) ?: createdAt,
                finishedAt = parseInstant(fields["finishedAt"]),
                corruptReason = decode(fields["corruptReasonB64"]).takeIf { it.isNotBlank() },
                metaFile = metaFile
            )
        } catch (failure: Exception) {
            corruptRecord(metaFile, "malformed queue record: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    private fun parseLease(fields: Map<String, String>): AgentQueueLease? {
        val token = fields["leaseToken"]?.takeIf { it.isNotBlank() } ?: return null
        val acquiredAt = parseInstant(fields["leaseAcquiredAt"]) ?: return null
        val heartbeatAt = parseInstant(fields["leaseHeartbeatAt"]) ?: acquiredAt
        val expiresAt = parseInstant(fields["leaseExpiresAt"]) ?: return null
        return AgentQueueLease(
            token = token,
            owner = decode(fields["leaseOwnerB64"]).ifBlank { "unknown" },
            acquiredAt = acquiredAt,
            heartbeatAt = heartbeatAt,
            expiresAt = expiresAt
        )
    }

    private fun corruptRecord(metaFile: Path, reason: String): AgentQueueRecord {
        val now = clock()
        return AgentQueueRecord(
            id = metaFile.fileName.toString().removeSuffix(".meta"),
            task = "",
            state = AgentQueueState.CORRUPT,
            checkpoint = AgentQueueCheckpoint.QUEUED,
            createdAt = now,
            updatedAt = now,
            finishedAt = now,
            corruptReason = reason,
            metaFile = metaFile
        )
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

    private fun parseInstant(value: String?): Instant? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        return runCatching { Instant.parse(text) }.getOrNull()
    }

    private fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private fun safeAtom(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "event" }

    private fun sanitizeEventMessage(value: String): String {
        return redactionFilter.compact(value, 240)
    }

    private fun sanitizeRecord(record: AgentQueueRecord): AgentQueueRecord =
        record.copy(
            task = sanitizeText(record.task, 8_000).orEmpty(),
            smokeCommand = sanitizeText(record.smokeCommand, 2_000),
            contextExportPath = sanitizeText(record.contextExportPath, 1_024),
            finalJobResult = sanitizeText(record.finalJobResult, 8_000),
            sourceEvidence = sanitizeText(record.sourceEvidence, 2_000),
            impactedSymbols = record.impactedSymbols
                .mapNotNull { sanitizeText(it, 512) }
                .distinct()
                .take(20),
            failureReason = sanitizeText(record.failureReason, 4_000),
            cancellationReason = sanitizeText(record.cancellationReason, 4_000),
            corruptReason = sanitizeText(record.corruptReason, 2_000)
        )

    private fun sanitizeText(value: String?, maxChars: Int): String? =
        value?.takeIf { it.isNotBlank() }?.let { redactionFilter.redact(it.trim()).take(maxChars) }
}
