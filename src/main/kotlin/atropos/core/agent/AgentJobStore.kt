package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

class AgentJobStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val jobDir = repoRoot.resolve(".atropos/agent/jobs").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneId.systemDefault())

    fun jobDirectory(): Path = jobDir

    fun createJob(task: String, provider: String): AgentJobRecord {
        Files.createDirectories(jobDir)
        val createdAt = clock()
        val id = nextJobId(createdAt, provider)
        val record = AgentJobRecord(
            id = id,
            task = task.trim(),
            status = AgentJobStatus.PLANNING,
            provider = provider.trim().ifBlank { "unknown" },
            createdAt = createdAt,
            updatedAt = createdAt,
            startedAt = createdAt,
            metaFile = jobDir.resolve("$id.meta")
        )
        writeRecord(record)
        return record
    }

    fun update(record: AgentJobRecord): AgentJobRecord {
        writeRecord(record)
        return record
    }

    fun resolve(reference: String): AgentJobRecord? {
        val jobId = resolveJobId(reference) ?: return null
        val metaFile = jobDir.resolve("$jobId.meta").normalize()
        if (!metaFile.startsWith(jobDir) || !Files.isRegularFile(metaFile)) return null
        return parseRecord(metaFile)
    }

    fun latest(): AgentJobRecord? =
        listJobs(limit = 1).firstOrNull()

    fun listJobs(limit: Int = 20): List<AgentJobRecord> {
        if (!Files.isDirectory(jobDir)) return emptyList()
        return try {
            Files.list(jobDir).use { stream ->
                stream
                    .map { it.normalize() }
                    .filter { it.fileName.toString().endsWith(".meta") && it.fileName.toString().startsWith("job-") }
                    .toList()
                    .mapNotNull { parseRecord(it) }
                    .sortedByDescending { it.id }
                    .take(limit.coerceAtLeast(0))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun renderList(limit: Int = 20): String = buildString {
        val jobs = listJobs(limit)
        if (jobs.isEmpty()) {
            appendLine("no agent jobs recorded")
            return@buildString
        }

        appendLine("recent agent jobs:")
        jobs.forEach { job ->
            appendLine("  ${job.renderSummaryLine()}")
        }
    }.trimEnd()

    private fun writeRecord(record: AgentJobRecord) {
        Files.createDirectories(jobDir)
        val tmp = Files.createTempFile(jobDir, record.id, ".tmp")
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("taskB64=${encode(record.task)}")
            appendLine("status=${record.status}")
            appendLine("provider=${record.provider}")
            appendLine("patchId=${record.patchId ?: ""}")
            appendLine("appliedPatchId=${record.appliedPatchId ?: ""}")
            appendLine("verificationId=${record.verificationId ?: ""}")
            appendLine("repairId=${record.repairId ?: ""}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("updatedAt=${record.updatedAt}")
            appendLine("startedAt=${record.startedAt}")
            appendLine("finishedAt=${record.finishedAt ?: ""}")
            appendLine("planAt=${record.planAt ?: ""}")
            appendLine("patchAt=${record.patchAt ?: ""}")
            appendLine("applyAt=${record.applyAt ?: ""}")
            appendLine("verificationAt=${record.verificationAt ?: ""}")
            appendLine("repairAt=${record.repairAt ?: ""}")
            appendLine("resultB64=${encode(record.result.orEmpty())}")
            appendLine("failureReasonB64=${encode(record.failureReason.orEmpty())}")
            appendLine("planB64=${encode(record.plan.orEmpty())}")
            appendLine("patchResultB64=${encode(record.patchResult.orEmpty())}")
            appendLine("applyResultB64=${encode(record.applyResult.orEmpty())}")
            appendLine("repairResultB64=${encode(record.repairResult.orEmpty())}")
            appendLine("metaFile=${record.metaFile.fileName}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parseRecord(metaFile: Path): AgentJobRecord? {
        val fields = try {
            Files.readAllLines(metaFile, StandardCharsets.UTF_8)
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) return@mapNotNull null
                    line.substring(0, index) to line.substring(index + 1)
                }
                .toMap()
        } catch (_: Exception) {
            return null
        }

        val id = fields["id"] ?: return null
        val task = decode(fields["taskB64"])
        val status = runCatching { AgentJobStatus.valueOf(fields["status"].orEmpty()) }.getOrNull() ?: AgentJobStatus.FAILED
        val provider = fields["provider"]?.takeIf { it.isNotBlank() } ?: "unknown"
        val createdAt = parseInstant(fields["createdAt"]) ?: return null
        val updatedAt = parseInstant(fields["updatedAt"]) ?: createdAt
        val startedAt = parseInstant(fields["startedAt"]) ?: createdAt
        val finishedAt = parseInstant(fields["finishedAt"])
        val planAt = parseInstant(fields["planAt"])
        val patchAt = parseInstant(fields["patchAt"])
        val applyAt = parseInstant(fields["applyAt"])
        val verificationAt = parseInstant(fields["verificationAt"])
        val repairAt = parseInstant(fields["repairAt"])

        return AgentJobRecord(
            id = id,
            task = task,
            status = status,
            provider = provider,
            patchId = fields["patchId"]?.takeIf { it.isNotBlank() },
            appliedPatchId = fields["appliedPatchId"]?.takeIf { it.isNotBlank() },
            verificationId = fields["verificationId"]?.takeIf { it.isNotBlank() },
            repairId = fields["repairId"]?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            planAt = planAt,
            patchAt = patchAt,
            applyAt = applyAt,
            verificationAt = verificationAt,
            repairAt = repairAt,
            result = decode(fields["resultB64"]).takeIf { it.isNotBlank() },
            failureReason = decode(fields["failureReasonB64"]).takeIf { it.isNotBlank() },
            plan = decode(fields["planB64"]).takeIf { it.isNotBlank() },
            patchResult = decode(fields["patchResultB64"]).takeIf { it.isNotBlank() },
            applyResult = decode(fields["applyResultB64"]).takeIf { it.isNotBlank() },
            repairResult = decode(fields["repairResultB64"]).takeIf { it.isNotBlank() },
            metaFile = metaFile
        )
    }

    private fun nextJobId(createdAt: Instant, provider: String): String {
        val timestamp = formatter.format(createdAt)
        val providerSlug = provider.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        var candidate = "job-$timestamp-$providerSlug"
        var suffix = 2
        while (Files.exists(jobDir.resolve("$candidate.meta"))) {
            candidate = "job-$timestamp-$providerSlug-$suffix"
            suffix++
        }
        return candidate
    }

    private fun resolveJobId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return latest()?.id

        val cleaned = trimmed.removeSuffix(".meta").removeSuffix(".job").trim()
        if (cleaned.isBlank() || cleaned.contains('/') || cleaned.contains('\\')) return null
        return cleaned
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
}
