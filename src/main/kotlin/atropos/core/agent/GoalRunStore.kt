package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
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
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val runsRoot = repoRoot.resolve(".atropos/runs").normalize()
    private val legacySelfHostRunsRoot = repoRoot.resolve(".atropos/self-hosting/runs").normalize()

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
        return writeRecord(record)
    }

    fun update(record: GoalRunRecord): GoalRunRecord {
        val updated = record.copy(updatedAt = clock())
        return writeRecord(updated)
    }

    fun resolve(reference: String): GoalRunRecord? {
        val id = resolveRunId(reference) ?: return null
        return runMetaFiles().firstOrNull { it.fileName.toString() == "$id.meta" }?.let(::parseRecord)
    }

    fun latest(): GoalRunRecord? = listRuns(1).firstOrNull()

    fun listRuns(limit: Int = 20): List<GoalRunRecord> {
        return runMetaFiles()
            .mapNotNull { parseRecord(it) }
            .sortedWith(
                compareByDescending<GoalRunRecord> { it.updatedAt }
                    .thenByDescending { it.createdAt }
            )
            .take(limit.coerceAtLeast(1))
    }

    private fun writeRecord(record: GoalRunRecord): GoalRunRecord {
        Files.createDirectories(runsRoot)
        val safe = sanitize(record)
        val tmp = Files.createTempFile(runsRoot, safe.id, ".tmp")
        val content = buildString {
            appendLine("id=${safe.id}")
            appendLine("goalId=${safe.goalId ?: ""}")
            appendLine("projectId=${safe.projectId ?: ""}")
            appendLine("dagId=${safe.dagId ?: ""}")
            appendLine("atomId=${safe.atomId ?: ""}")
            appendLine("taskB64=${encode(safe.task)}")
            appendLine("provider=${safe.provider ?: ""}")
            appendLine("status=${safe.status}")
            appendLine("terminalCondition=${safe.terminalCondition ?: ""}")
            appendLine("continuationCount=${safe.continuationCount}")
            appendLine("maxContinuations=${safe.maxContinuations}")
            appendLine("lastContinuationAt=${safe.lastContinuationAt ?: ""}")
            appendLine("compactStateB64=${encode(safe.compactState.orEmpty())}")
            appendLine("lastProviderResponseId=${safe.lastProviderResponseId ?: ""}")
            appendLine("failureReasonB64=${encode(safe.failureReason.orEmpty())}")
            appendLine("parentRunId=${safe.parentRunId ?: ""}")
            appendLine("runId=${safe.runId ?: ""}")
            appendLine("baselineCommit=${safe.baselineCommit ?: ""}")
            appendLine("dirtyStateFingerprint=${safe.dirtyStateFingerprint ?: ""}")
            appendLine("activePhase=${safe.activePhase ?: ""}")
            appendLine("currentNodeId=${safe.currentNodeId ?: ""}")
            appendLine("territoryB64=${encode(safe.territory.joinToString("\u0000"))}")
            appendLine("evidenceB64=${encode(safe.evidence.joinToString("|"))}")
            safe.evidence.forEach { appendLine("evidenceEntryB64=${encode(it)}") }
            appendLine("retryBudget=${safe.retryBudget}")
            appendLine("lastVerifiedCheckpoint=${safe.lastVerifiedCheckpoint ?: ""}")
            appendLine("createdAt=${safe.createdAt}")
            appendLine("updatedAt=${safe.updatedAt}")
            appendLine("finishedAt=${safe.finishedAt ?: ""}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, safe.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, safe.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
        return safe
    }

    private fun parseRecord(metaFile: Path): GoalRunRecord? {
        val pairs = runCatching {
            Files.readAllLines(metaFile, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
        }.getOrNull() ?: return null
        val fields = pairs.toMap()
        val evidenceEntries = pairs
            .filter { it.first == "evidenceEntryB64" }
            .mapNotNull { decode(it.second).takeIf { decoded -> decoded.isNotBlank() } }
        val legacyEvidence = decode(fields["evidenceB64"]).split("|").filter { it.isNotBlank() }
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
                territory = if (fields["territoryB64"].isNullOrBlank()) {
                    fields["territory"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                } else {
                    decode(fields["territoryB64"]).split("\u0000").filter { it.isNotBlank() }
                },
                evidence = evidenceEntries.ifEmpty { legacyEvidence },
                retryBudget = fields["retryBudget"]?.toIntOrNull() ?: 10,
                lastVerifiedCheckpoint = fields["lastVerifiedCheckpoint"]?.takeIf { it.isNotBlank() },
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                finishedAt = parseInstant(fields["finishedAt"]),
                metaFile = metaFile
            )
        }.getOrNull()
    }

    private fun sanitize(record: GoalRunRecord): GoalRunRecord =
        record.copy(
            task = redactionFilter.redact(record.task).trim(),
            provider = record.provider?.let(redactionFilter::redact),
            compactState = record.compactState?.let(redactionFilter::redact),
            failureReason = record.failureReason?.let(redactionFilter::redact),
            territory = record.territory.map(redactionFilter::redact),
            evidence = record.evidence.map(redactionFilter::redact),
            lastVerifiedCheckpoint = record.lastVerifiedCheckpoint?.let(redactionFilter::redact)
        )

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

    private fun runMetaFiles(): List<Path> {
        val roots = listOf(runsRoot, legacySelfHostRunsRoot)
        return roots
            .filter { Files.isDirectory(it) }
            .flatMap { root ->
                Files.list(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".meta") }
                        .toList()
                }
            }
            .distinctBy { it.normalize().toString() }
    }

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
