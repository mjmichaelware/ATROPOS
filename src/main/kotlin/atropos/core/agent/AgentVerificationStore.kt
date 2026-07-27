package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

data class AgentVerificationRecord(
    val id: String,
    val patchId: String,
    val createdAt: Instant,
    val command: String,
    val exitCode: Int?,
    val durationMillis: Long,
    val changedPaths: List<String>,
    val stdout: String,
    val stderr: String,
    val passed: Boolean,
    val failureReason: String?,
    val metaFile: Path
)

data class AgentVerificationRunResult(
    val patchId: String?,
    val verificationId: String?,
    val patchFile: Path?,
    val command: String? = null,
    val exitCode: Int? = null,
    val durationMillis: Long = 0L,
    val changedPaths: List<String> = emptyList(),
    val stdout: String = "",
    val stderr: String = "",
    val passed: Boolean = false,
    val metaFile: Path? = null,
    val refusalReason: String? = null
) {
    fun render(): String = buildString {
        val filter = RedactionFilter()
        appendLine("Patch id: ${patchId ?: "none"}")
        verificationId?.let { appendLine("Verification id: $it") }
        command?.let { appendLine("Command: ${filter.redact(it)}") }
        patchFile?.let { appendLine("Patch path: ${filter.redact(it.toString())}") }
        appendLine("Changed paths: ${changedPaths.joinToString(", ") { filter.redact(it) }.ifBlank { "none" }}")
        exitCode?.let { appendLine("Exit code: $it") }
        if (durationMillis > 0) appendLine("Duration ms: $durationMillis")
        appendLine("Result: ${if (passed) "PASSED" else "FAILED"}")
        if (stdout.isNotBlank()) appendLine("stdout: ${compact(stdout)}")
        if (stderr.isNotBlank()) appendLine("stderr: ${compact(stderr)}")
        metaFile?.let { appendLine("Verification metadata: $it") }
        refusalReason?.takeIf { it.isNotBlank() }?.let { appendLine("Refusal reason: ${filter.redact(it)}") }
    }.trimEnd()

    private fun compact(text: String, maxChars: Int = 600): String {
        return RedactionFilter().compact(text, maxChars)
    }
}

class AgentVerificationStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val verificationDir = repoRoot.resolve(".atropos/agent/patches").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withZone(ZoneId.systemDefault())

    fun verificationDirectory(): Path = verificationDir

    fun createRecord(
        patchId: String,
        command: String,
        exitCode: Int?,
        durationMillis: Long,
        changedPaths: List<String>,
        stdout: String,
        stderr: String,
        passed: Boolean,
        failureReason: String?
    ): AgentVerificationRecord {
        Files.createDirectories(verificationDir)
        val createdAt = clock()
        val id = nextVerificationId(createdAt, patchId)
        val metaFile = verificationDir.resolve("$id.meta")
        val record = AgentVerificationRecord(
            id = id,
            patchId = patchId,
            createdAt = createdAt,
            command = redactionFilter.redact(command.trim()),
            exitCode = exitCode,
            durationMillis = durationMillis,
            changedPaths = changedPaths.map { redactionFilter.redact(it.trim()) }.filter { it.isNotBlank() }.distinct(),
            stdout = redactionFilter.redact(stdout),
            stderr = redactionFilter.redact(stderr),
            passed = passed,
            failureReason = failureReason?.trim()?.takeIf { it.isNotBlank() }?.let(redactionFilter::redact),
            metaFile = metaFile
        )
        writeRecord(record)
        return record
    }

    fun writeRecord(record: AgentVerificationRecord) {
        Files.createDirectories(verificationDir)
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("patchId=${record.patchId}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("commandB64=${encode(record.command)}")
            appendLine("exitCode=${record.exitCode ?: ""}")
            appendLine("durationMillis=${record.durationMillis}")
            appendLine("changedPathsB64=${encode(record.changedPaths.joinToString("\n"))}")
            appendLine("passed=${record.passed}")
            appendLine("failureReasonB64=${encode(record.failureReason.orEmpty())}")
            appendLine("stdoutB64=${encode(record.stdout)}")
            appendLine("stderrB64=${encode(record.stderr)}")
        }
        Files.writeString(record.metaFile, content, StandardCharsets.UTF_8)
    }

    fun latestRecord(patchId: String): AgentVerificationRecord? =
        listVerificationRecords(patchId).firstOrNull()

    fun latestFailedRecord(patchId: String): AgentVerificationRecord? =
        listVerificationRecords(patchId).firstOrNull { !it.passed }

    fun listVerificationRecords(patchId: String): List<AgentVerificationRecord> {
        if (!Files.isDirectory(verificationDir)) return emptyList()
        return try {
            Files.list(verificationDir).use { stream ->
                stream
                    .map { it.normalize() }
                    .filter { it.fileName.toString().endsWith(".meta") && it.fileName.toString().startsWith("verify-") }
                    .toList()
                    .mapNotNull { parseRecord(it) }
                    .filter { it.patchId == patchId }
                    .sortedByDescending { it.id }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRecord(metaFile: Path): AgentVerificationRecord? {
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
        val patchId = fields["patchId"] ?: return null
        val createdAtText = fields["createdAt"] ?: return null
        val createdAt = runCatching { Instant.parse(createdAtText) }.getOrNull() ?: return null
        val command = decode(fields["commandB64"])
        val exitCode = fields["exitCode"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
        val durationMillis = fields["durationMillis"]?.toLongOrNull() ?: 0L
        val changedPaths = decode(fields["changedPathsB64"])
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val passed = fields["passed"]?.toBooleanStrictOrNull() ?: false
        val failureReason = decode(fields["failureReasonB64"]).trim().takeIf { it.isNotBlank() }
        val stdout = decode(fields["stdoutB64"])
        val stderr = decode(fields["stderrB64"])

        return AgentVerificationRecord(
            id = id,
            patchId = patchId,
            createdAt = createdAt,
            command = command,
            exitCode = exitCode,
            durationMillis = durationMillis,
            changedPaths = changedPaths,
            stdout = stdout,
            stderr = stderr,
            passed = passed,
            failureReason = failureReason,
            metaFile = metaFile
        )
    }

    private fun nextVerificationId(createdAt: Instant, patchId: String): String {
        val timestamp = formatter.format(createdAt)
        val patchSlug = patchId.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        var candidate = "verify-$timestamp-$patchSlug"
        var suffix = 2
        while (Files.exists(verificationDir.resolve("$candidate.meta"))) {
            candidate = "verify-$timestamp-$patchSlug-$suffix"
            suffix++
        }
        return candidate
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
