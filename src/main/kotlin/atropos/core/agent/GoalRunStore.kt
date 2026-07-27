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
import java.util.UUID

class GoalRunStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val runsRoot = repoRoot.resolve(".atropos/runs").normalize()

    fun runsRoot(): Path = runsRoot

    fun createGoalRun(task: String, provider: String? = null, parentRunId: String? = null): GoalRunRecord {
        Files.createDirectories(runsRoot)
        val now = clock()
        val id = "goal-" + UUID.randomUUID().toString().take(12)
        val record = GoalRunRecord(
            id = id,
            task = task.trim(),
            provider = provider,
            parentRunId = parentRunId,
            createdAt = now,
            updatedAt = now,
            metaFile = runsRoot.resolve("$id.meta")
        )
        writeRecord(record)
        return record
    }

    fun update(record: GoalRunRecord): GoalRunRecord {
        val updated = record.copy(updatedAt = clock())
        writeRecord(updated)
        return updated
    }

    fun resolve(reference: String): GoalRunRecord? {
        val id = resolveRunId(reference) ?: return null
        val metaFile = runsRoot.resolve("$id.meta").normalize()
        if (!metaFile.startsWith(runsRoot) || !Files.isRegularFile(metaFile)) return null
        return parseRecord(metaFile)
    }

    fun latest(): GoalRunRecord? = listRuns(1).firstOrNull()

    fun listRuns(limit: Int = 20): List<GoalRunRecord> {
        if (!Files.isDirectory(runsRoot)) return emptyList()
        val files = Files.list(runsRoot).use { stream -> stream.toList() }
        return files
            .filter { it.fileName.toString().endsWith(".meta") && it.fileName.toString().startsWith("goal-") }
            .mapNotNull { parseRecord(it) }
            .sortedByDescending { it.createdAt }
            .take(limit.coerceAtLeast(1))
    }

    private fun writeRecord(record: GoalRunRecord) {
        Files.createDirectories(runsRoot)
        val tmp = Files.createTempFile(runsRoot, record.id, ".tmp")
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("goalId=${record.goalId ?: ""}")
            appendLine("projectId=${record.projectId ?: ""}")
            appendLine("dagId=${record.dagId ?: ""}")
            appendLine("atomId=${record.atomId ?: ""}")
            appendLine("taskB64=${encode(record.task)}")
            appendLine("provider=${record.provider ?: ""}")
            appendLine("status=${record.status}")
            appendLine("terminalCondition=${record.terminalCondition ?: ""}")
            appendLine("continuationCount=${record.continuationCount}")
            appendLine("maxContinuations=${record.maxContinuations}")
            appendLine("lastContinuationAt=${record.lastContinuationAt ?: ""}")
            appendLine("compactStateB64=${encode(record.compactState.orEmpty())}")
            appendLine("lastProviderResponseId=${record.lastProviderResponseId ?: ""}")
            appendLine("failureReasonB64=${encode(record.failureReason.orEmpty())}")
            appendLine("parentRunId=${record.parentRunId ?: ""}")
            appendLine("runId=${record.runId ?: ""}")
            appendLine("baselineCommit=${record.baselineCommit ?: ""}")
            appendLine("dirtyStateFingerprint=${record.dirtyStateFingerprint ?: ""}")
            appendLine("activePhase=${record.activePhase ?: ""}")
            appendLine("currentNodeId=${record.currentNodeId ?: ""}")
            appendLine("territory=${record.territory.joinToString(",")}")
            appendLine("evidenceB64=${encode(record.evidence.joinToString("|"))}")
            appendLine("retryBudget=${record.retryBudget}")
            appendLine("lastVerifiedCheckpoint=${record.lastVerifiedCheckpoint ?: ""}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("updatedAt=${record.updatedAt}")
            appendLine("finishedAt=${record.finishedAt ?: ""}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parseRecord(metaFile: Path): GoalRunRecord? {
        val fields = runCatching {
            Files.readAllLines(metaFile, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        return runCatching {
            GoalRunRecord(
                id = fields["id"].orEmpty(),
                goalId = fields["goalId"]?.takeIf { it.isNotBlank() },
                projectId = fields["projectId"]?.takeIf { it.isNotBlank() },
                dagId = fields["dagId"]?.takeIf { it.isNotBlank() },
                atomId = fields["atomId"]?.takeIf { it.isNotBlank() },
                task = decode(fields["taskB64"]),
                provider = fields["provider"]?.takeIf { it.isNotBlank() },
                status = GoalRunStatus.valueOf(fields["status"].orEmpty()),
                terminalCondition = fields["terminalCondition"]?.takeIf { it.isNotBlank() }?.let { GoalTerminalCondition.valueOf(it) },
                continuationCount = fields["continuationCount"]?.toIntOrNull() ?: 0,
                maxContinuations = fields["maxContinuations"]?.toIntOrNull() ?: 10,
                lastContinuationAt = parseInstant(fields["lastContinuationAt"]),
                compactState = decode(fields["compactStateB64"]).takeIf { it.isNotBlank() },
                lastProviderResponseId = fields["lastProviderResponseId"]?.takeIf { it.isNotBlank() },
                failureReason = decode(fields["failureReasonB64"]).takeIf { it.isNotBlank() },
                parentRunId = fields["parentRunId"]?.takeIf { it.isNotBlank() },
                runId = fields["runId"]?.takeIf { it.isNotBlank() },
                baselineCommit = fields["baselineCommit"]?.takeIf { it.isNotBlank() },
                dirtyStateFingerprint = fields["dirtyStateFingerprint"]?.takeIf { it.isNotBlank() },
                activePhase = fields["activePhase"]?.takeIf { it.isNotBlank() },
                currentNodeId = fields["currentNodeId"]?.takeIf { it.isNotBlank() },
                territory = fields["territory"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                evidence = decode(fields["evidenceB64"]).split("|").filter { it.isNotBlank() },
                retryBudget = fields["retryBudget"]?.toIntOrNull() ?: 10,
                lastVerifiedCheckpoint = fields["lastVerifiedCheckpoint"]?.takeIf { it.isNotBlank() },
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                finishedAt = parseInstant(fields["finishedAt"]),
                metaFile = metaFile
            )
        }.getOrNull()
    }

    private fun resolveRunId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return latest()?.id
        val cleaned = trimmed.removeSuffix(".meta").trim()
        if (cleaned.isBlank() || cleaned.contains("/") || cleaned.contains("\\")) return null
        return cleaned
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
