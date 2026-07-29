package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Base64

internal class AgentQueueRecordCodec(
    private val entriesDir: Path,
    private val clock: () -> Instant,
    private val redactionFilter: RedactionFilter
) {
    fun write(record: AgentQueueRecord) {
        Files.createDirectories(entriesDir)
        val tmp = Files.createTempFile(entriesDir, record.id, ".tmp")
        val bytes = render(record).toByteArray(StandardCharsets.UTF_8)
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

    fun parse(metaFile: Path): AgentQueueRecord {
        val fields = try {
            Files.readAllLines(metaFile, StandardCharsets.UTF_8)
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    line.substring(0, index) to line.substring(index + 1)
                }
                .toMap()
        } catch (failure: Exception) {
            return corrupt(metaFile, "unable to read record: ${failure.message ?: failure.javaClass.simpleName}")
        }

        return try {
            val id = fields["id"]?.takeIf { it.isNotBlank() } ?: metaFile.fileName.toString().removeSuffix(".meta")
            val createdAt = parseInstant(fields["createdAt"]) ?: throw IllegalArgumentException("missing createdAt")
            val state = enumValueOf<AgentQueueState>(fields["state"] ?: throw IllegalArgumentException("missing state"))
            val checkpoint = enumValueOf<AgentQueueCheckpoint>(fields["checkpoint"] ?: AgentQueueCheckpoint.QUEUED.name)
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
                lease = parseLease(fields),
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
            corrupt(metaFile, "malformed queue record: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    fun sanitize(record: AgentQueueRecord): AgentQueueRecord =
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

    private fun render(record: AgentQueueRecord): String = buildString {
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

    private fun corrupt(metaFile: Path, reason: String): AgentQueueRecord {
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

    private fun sanitizeText(value: String?, maxChars: Int): String? =
        value?.takeIf { it.isNotBlank() }?.let { redactionFilter.redact(it.trim()).take(maxChars) }
}
