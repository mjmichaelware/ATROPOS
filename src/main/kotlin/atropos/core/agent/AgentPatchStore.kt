package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AgentPatchRecord(
    val id: String,
    val provider: String,
    val createdAt: Instant,
    val task: String,
    val contextBytes: Int,
    val diffBytes: Int,
    val patchDir: Path,
    val diffFile: Path,
    val metaFile: Path
)

data class AgentPatchCheckResult(
    val passed: Boolean,
    val exitCode: Int,
    val output: String
) {
    val statusText: String
        get() = if (passed) "OK" else "FAILED"
}

data class AgentPatchSnapshot(
    val id: String,
    val patchFile: Path,
    val metaFile: Path,
    val diffText: String,
    val extraction: AgentPatchExtraction
)

data class AgentPatchApplyResult(
    val patchId: String?,
    val patchFile: Path?,
    val changedPaths: List<String> = emptyList(),
    val checkOnly: Boolean,
    val applied: Boolean,
    val checkResult: AgentPatchCheckResult? = null,
    val applyExitCode: Int? = null,
    val applyOutput: String? = null,
    val refusalReason: String? = null,
    val logFile: Path? = null
) {
    fun render(): String = buildString {
        appendLine("Patch id: ${patchId ?: "none"}")
        appendLine("Patch path: ${patchFile ?: "none"}")
        appendLine("Changed paths: ${changedPaths.joinToString(", ").ifBlank { "none" }}")
        if (checkOnly) {
            appendLine(
                if (checkResult?.passed == true && refusalReason.isNullOrBlank()) {
                    "APPLY CHECK OK"
                } else {
                    "APPLY CHECK FAILED: ${refusalReason ?: checkResult?.output ?: "unknown"}"
                }
            )
        } else {
            appendLine(
                if (applied) {
                    "APPLY OK"
                } else {
                    "APPLY REFUSED: ${refusalReason ?: checkResult?.output ?: "unknown"}"
                }
            )
        }
        checkResult?.let {
            appendLine("git apply --check: ${it.statusText}${it.output.takeIf { output -> output.isNotBlank() }?.let { output -> " :: $output" } ?: ""}")
        }
        applyExitCode?.let { appendLine("git apply exit code: $it") }
        logFile?.let { appendLine("Apply log: $it") }
        if (applied) {
            val verifyCommand = if (changedPaths.isNotEmpty()) {
                "git diff -- ${changedPaths.joinToString(" ")}"
            } else {
                "git status --short"
            }
            appendLine("Next command to verify: $verifyCommand")
        }
        refusalReason?.takeIf { it.isNotBlank() && !checkOnly }?.let { appendLine("Refusal reason: $it") }
    }.trimEnd()
}

class AgentPatchStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val extractor: AgentPatchExtractor = AgentPatchExtractor()
) {
    private val patchDir = repoRoot.resolve(".atropos/agent/patches").normalize()
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    fun createRecord(provider: String, task: String, contextBytes: Int, diff: String): AgentPatchRecord {
        Files.createDirectories(patchDir)
        val createdAt = clock()
        val id = nextPatchId(createdAt, provider)
        val diffFile = patchDir.resolve("$id.diff")
        val metaFile = patchDir.resolve("$id.meta")
        val renderedDiff = diff.trimEnd() + "\n"
        Files.writeString(diffFile, renderedDiff, StandardCharsets.UTF_8)
        return AgentPatchRecord(
            id = id,
            provider = provider,
            createdAt = createdAt,
            task = task.trim(),
            contextBytes = contextBytes,
            diffBytes = renderedDiff.toByteArray(StandardCharsets.UTF_8).size,
            patchDir = patchDir,
            diffFile = diffFile,
            metaFile = metaFile
        )
    }

    fun writeMeta(record: AgentPatchRecord, check: AgentPatchCheckResult) {
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("provider=${record.provider}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("task=${record.task.replace("\n", " ").trim()}")
            appendLine("contextBytes=${record.contextBytes}")
            appendLine("diffBytes=${record.diffBytes}")
            appendLine("gitApplyCheckStatus=${check.statusText}")
            appendLine("gitApplyCheckExitCode=${check.exitCode}")
            appendLine("gitApplyCheckOutput=${compactOutput(check.output)}")
            appendLine("diffFile=${record.diffFile.fileName}")
        }
        Files.writeString(record.metaFile, content, StandardCharsets.UTF_8)
    }

    fun latestPatchId(): String? {
        if (!Files.isDirectory(patchDir)) return null
        return try {
            Files.list(patchDir).use { stream ->
                stream
                    .map { it.fileName.toString() }
                    .filter { it.endsWith(".diff") }
                    .map { it.removeSuffix(".diff") }
                    .sorted()
                    .reduce { _, current -> current }
                    .orElse(null)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun patchDirectory(): Path = patchDir

    fun resolvePatchSnapshot(reference: String): AgentPatchSnapshot? {
        val patchId = resolvePatchId(reference) ?: return null
        val diffFile = patchDir.resolve("$patchId.diff").normalize()
        if (!diffFile.startsWith(patchDir) || !Files.isRegularFile(diffFile)) return null

        val metaFile = patchDir.resolve("$patchId.meta").normalize()
        val diffText = Files.readString(diffFile, StandardCharsets.UTF_8)
        val extraction = extractor.extract(diffText) ?: return null
        return AgentPatchSnapshot(
            id = patchId,
            patchFile = diffFile,
            metaFile = metaFile,
            diffText = diffText,
            extraction = extraction
        )
    }

    fun runGitApplyCheck(diffFile: Path): AgentPatchCheckResult {
        val process = ProcessBuilder("git", "apply", "--check", diffFile.toString())
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        return AgentPatchCheckResult(
            passed = exitCode == 0,
            exitCode = exitCode,
            output = compactOutput(output)
        )
    }

    fun runGitApply(diffFile: Path): AgentPatchCheckResult {
        val process = ProcessBuilder("git", "apply", diffFile.toString())
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        return AgentPatchCheckResult(
            passed = exitCode == 0,
            exitCode = exitCode,
            output = compactOutput(output)
        )
    }

    fun runGitStatusForPaths(paths: List<String>): String {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanPaths.isEmpty()) return ""

        val command = mutableListOf("git", "status", "--porcelain", "--untracked-files=all", "--")
        command.addAll(cleanPaths)

        val process = ProcessBuilder(command)
            .directory(repoRoot.toFile())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return output
    }

    fun applyPatch(reference: String, checkOnly: Boolean): AgentPatchApplyResult {
        val resolvedId = resolvePatchId(reference)
        val resolvedPath = resolvedId?.let { patchDir.resolve("$it.diff").normalize() }
        if (resolvedId == null || resolvedPath == null || !resolvedPath.startsWith(patchDir) || !Files.isRegularFile(resolvedPath)) {
            return AgentPatchApplyResult(
                patchId = resolvedId,
                patchFile = resolvedPath,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = "patch not found: ${reference.trim()}"
            )
        }

        val metaFile = patchDir.resolve("$resolvedId.meta").normalize()
        val diffText = Files.readString(resolvedPath, StandardCharsets.UTF_8)
        val extraction = extractor.extract(diffText)
            ?: return AgentPatchApplyResult(
                patchId = resolvedId,
                patchFile = resolvedPath,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = "stored patch is not a valid unified diff"
            )
        val snapshot = AgentPatchSnapshot(
            id = resolvedId,
            patchFile = resolvedPath,
            metaFile = metaFile,
            diffText = diffText,
            extraction = extraction
        )

        val validationFailure = extractor.validate(snapshot.extraction.diff)
        if (validationFailure != null) {
            val logFile = if (!checkOnly) {
                writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = AgentPatchCheckResult(passed = false, exitCode = 0, output = validationFailure),
                    applyResult = null,
                    refusalReason = validationFailure,
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                refusalReason = validationFailure,
                logFile = logFile
            )
        }

        val checkResult = runGitApplyCheck(snapshot.patchFile)
        if (!checkResult.passed) {
            val logFile = if (!checkOnly) {
                writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = checkResult,
                    applyResult = null,
                    refusalReason = "git apply --check failed: ${checkResult.output}",
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                checkResult = checkResult,
                refusalReason = "git apply --check failed: ${checkResult.output}",
                logFile = logFile
            )
        }

        val dirtyTargetStatus = runGitStatusForPaths(snapshot.extraction.touchedPaths)
        if (dirtyTargetStatus.isNotBlank()) {
            val refusal = "target files have uncommitted changes: $dirtyTargetStatus"
            val logFile = if (!checkOnly) {
                writeApplyMeta(
                    snapshot = snapshot,
                    checkOnly = false,
                    checkResult = checkResult,
                    applyResult = null,
                    refusalReason = refusal,
                    changedPaths = snapshot.extraction.touchedPaths
                )
            } else null
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = checkOnly,
                applied = false,
                checkResult = checkResult,
                refusalReason = refusal,
                logFile = logFile
            )
        }

        if (checkOnly) {
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = true,
                applied = false,
                checkResult = checkResult
            )
        }

        val applyResult = runGitApply(snapshot.patchFile)
        if (!applyResult.passed) {
            val refusal = "git apply failed: ${applyResult.output}"
            val logFile = writeApplyMeta(
                snapshot = snapshot,
                checkOnly = false,
                checkResult = checkResult,
                applyResult = applyResult,
                refusalReason = refusal,
                changedPaths = snapshot.extraction.touchedPaths
            )
            return AgentPatchApplyResult(
                patchId = snapshot.id,
                patchFile = snapshot.patchFile,
                changedPaths = snapshot.extraction.touchedPaths,
                checkOnly = false,
                applied = false,
                checkResult = checkResult,
                applyExitCode = applyResult.exitCode,
                applyOutput = applyResult.output,
                refusalReason = refusal,
                logFile = logFile
            )
        }

        val logFile = writeApplyMeta(
            snapshot = snapshot,
            checkOnly = false,
            checkResult = checkResult,
            applyResult = applyResult,
            refusalReason = null,
            changedPaths = snapshot.extraction.touchedPaths
        )

        return AgentPatchApplyResult(
            patchId = snapshot.id,
            patchFile = snapshot.patchFile,
            changedPaths = snapshot.extraction.touchedPaths,
            checkOnly = false,
            applied = true,
            checkResult = checkResult,
            applyExitCode = applyResult.exitCode,
            applyOutput = applyResult.output,
            logFile = logFile
        )
    }

    private fun nextPatchId(createdAt: Instant, provider: String): String {
        val timestamp = formatter.format(createdAt)
        val providerSlug = provider.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        var candidate = "patch-$timestamp-$providerSlug"
        var suffix = 2
        while (Files.exists(patchDir.resolve("$candidate.diff")) || Files.exists(patchDir.resolve("$candidate.meta"))) {
            candidate = "patch-$timestamp-$providerSlug-$suffix"
            suffix++
        }
        return candidate
    }

    private fun resolvePatchId(reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("latest", ignoreCase = true)) return latestPatchId()

        val cleaned = trimmed
            .removeSuffix(".diff")
            .removeSuffix(".meta")
            .trim()

        if (cleaned.isBlank() || cleaned.contains('/') || cleaned.contains('\\')) return null
        return cleaned
    }

    private fun writeApplyMeta(
        snapshot: AgentPatchSnapshot,
        checkOnly: Boolean,
        checkResult: AgentPatchCheckResult,
        applyResult: AgentPatchCheckResult?,
        refusalReason: String?,
        changedPaths: List<String>
    ): Path {
        Files.createDirectories(patchDir)
        val createdAt = clock()
        val logFile = patchDir.resolve("apply-${formatter.format(createdAt)}-${snapshot.id}.meta")
        val content = buildString {
            appendLine("patchId=${snapshot.id}")
            appendLine("patchFile=${snapshot.patchFile.fileName}")
            appendLine("checkOnly=$checkOnly")
            appendLine("applied=${applyResult?.passed == true && refusalReason == null}")
            appendLine("changedPaths=${changedPaths.joinToString(",")}")
            appendLine("gitApplyCheckStatus=${checkResult.statusText}")
            appendLine("gitApplyCheckExitCode=${checkResult.exitCode}")
            appendLine("gitApplyCheckOutput=${compactOutput(checkResult.output)}")
            appendLine("gitApplyExitCode=${applyResult?.exitCode ?: ""}")
            appendLine("gitApplyOutput=${compactOutput(applyResult?.output.orEmpty())}")
            appendLine("refusalReason=${refusalReason ?: ""}")
        }
        Files.writeString(logFile, content, StandardCharsets.UTF_8)
        return logFile
    }

    private fun compactOutput(raw: String, maxLines: Int = 8, maxChars: Int = 1200): String {
        if (raw.isBlank()) return "no output"
        val lines = raw.lineSequence().take(maxLines).joinToString(" | ").trim()
        return if (lines.length <= maxChars) lines else lines.take(maxChars - 3) + "..."
    }
}
