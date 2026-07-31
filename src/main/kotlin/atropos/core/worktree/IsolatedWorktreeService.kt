package atropos.core.worktree

import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.CredentialDiffGuard
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

data class WorktreeRecord(
    val id: String,
    val jobId: String,
    val worktreePath: Path,
    val baselineCommit: String? = null,
    val territory: List<String> = emptyList(),
    val dirtyEvidence: String? = null,
    val appliedPatches: List<String> = emptyList(),
    val verified: Boolean = false,
    val rolledBack: Boolean = false,
    val mergedBack: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metaFile: Path
)

data class WorktreeCreateResult(
    val ok: Boolean,
    val message: String,
    val record: WorktreeRecord? = null
)

data class WorktreeRollbackResult(
    val ok: Boolean,
    val message: String,
    val revertedFiles: List<String> = emptyList()
)

class IsolatedWorktreeService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val credentialDiffGuard: CredentialDiffGuard = CredentialDiffGuard(),
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    private val worktreeRoot = repoRoot.resolve(".atropos/worktrees").normalize()

    fun createWorktree(jobId: String, territory: List<String> = emptyList()): WorktreeCreateResult {
        try {
            Files.createDirectories(worktreeRoot)

            // Capture baseline commit
            val baselineCommit = gitRunner.run(GitWorktreeOperation.REV_PARSE_HEAD, repoRoot)
                .takeIf { it.exitCode == 0 }
                ?.output
                ?.trim()

            // Capture dirty state
            val dirtyEvidence = gitRunner.run(GitWorktreeOperation.STATUS_PORCELAIN, repoRoot)
                .takeIf { it.exitCode == 0 }
                ?.output
                ?.trim()

            val id = "wt-" + UUID.randomUUID().toString().take(12)
            val wtDir = worktreeRoot.resolve(id)

            if (baselineCommit.isNullOrBlank()) {
                return WorktreeCreateResult(false, "failed to create worktree: baseline commit unavailable")
            }

            val worktreeOutput = gitRunner.run(
                GitWorktreeOperation.WORKTREE_ADD,
                repoRoot,
                wtDir.toString(),
                baselineCommit
            )
            if (worktreeOutput.exitCode != 0 || !Files.exists(wtDir.resolve(".git"))) {
                return WorktreeCreateResult(
                    false,
                    "failed to create worktree: ${redactionFilter.compact(worktreeOutput.output)}"
                )
            }

            val now = clock()
            val record = WorktreeRecord(
                id = id,
                jobId = jobId,
                worktreePath = wtDir,
                baselineCommit = baselineCommit,
                territory = territory,
                dirtyEvidence = dirtyEvidence?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                metaFile = worktreeRoot.resolve("$id.meta")
            )
            writeRecord(record)

            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.NOTE,
                title = "worktree created: $id",
                body = "job=$jobId territory=${territory.joinToString(",")}",
                tags = listOf("worktree", "created"),
                subjectType = "worktree",
                subjectId = id
            )

            return WorktreeCreateResult(true, "worktree $id created at $wtDir", record)
        } catch (e: Exception) {
            return WorktreeCreateResult(false, "failed to create worktree: ${e.message}")
        }
    }

    fun applyPatch(worktreeId: String, patchContent: String): Boolean {
        val record = readRecord(worktreeId) ?: return false
        if (patchContent.isBlank()) {
            recordTerritoryViolation(record, "<empty-patch>", "patch_apply_empty")
            return false
        }
        val patchPaths = extractPatchPaths(patchContent)
        val unsafePath = patchPaths.firstOrNull(::isUnsafeRelativePath)
        if (unsafePath != null) {
            recordTerritoryViolation(record, unsafePath, "patch_apply_path")
            return false
        }
        val outside = firstOutsideTerritory(record, patchPaths)
        if (outside != null) {
            recordTerritoryViolation(record, outside, "patch_apply")
            return false
        }
        return runCatching {
            val result = gitRunner.run(GitWorktreeOperation.APPLY_PATCH, record.worktreePath, input = patchContent)
            if (result.exitCode == 0) {
                val updated = record.copy(
                    appliedPatches = record.appliedPatches + patchContent.take(40),
                    updatedAt = clock()
                )
                writeRecord(updated)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Writes one non-empty, territory-bound file in an existing worktree. */
    fun writeFile(worktreeId: String, relativePath: String, content: String): Boolean {
        val record = readRecord(worktreeId) ?: return false
        val path = relativePath.trim()
        if (content.isBlank()) {
            recordTerritoryViolation(record, path.ifBlank { "<empty-path>" }, "file_write_empty")
            return false
        }
        if (isUnsafeRelativePath(path)) {
            recordTerritoryViolation(record, path, "file_write_path")
            return false
        }
        val outside = firstOutsideTerritory(record, listOf(path))
        if (outside != null) {
            recordTerritoryViolation(record, outside, "file_write")
            return false
        }
        val target = record.worktreePath.resolve(path).normalize()
        if (!target.startsWith(record.worktreePath.toAbsolutePath().normalize())) {
            recordTerritoryViolation(record, path, "file_write_escape")
            return false
        }
        return runCatching {
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, content, StandardCharsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    /** Stages intent for one new self-host file through the typed Git boundary. */
    fun intentToAdd(worktreeId: String, relativePath: String): WorktreeRollbackResult {
        val record = readRecord(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")
        val path = relativePath.trim()
        if (isUnsafeRelativePath(path)) {
            recordTerritoryViolation(record, path, "intent_to_add_path")
            return WorktreeRollbackResult(false, "intent-to-add path refused: $path")
        }
        val outside = firstOutsideTerritory(record, listOf(path))
        if (outside != null) {
            recordTerritoryViolation(record, outside, "intent_to_add")
            return WorktreeRollbackResult(false, "intent-to-add territory violation: $outside")
        }
        val target = record.worktreePath.resolve(path).normalize()
        if (!target.startsWith(record.worktreePath.toAbsolutePath().normalize())) {
            recordTerritoryViolation(record, path, "intent_to_add_escape")
            return WorktreeRollbackResult(false, "intent-to-add path escaped worktree: $path")
        }
        if (!Files.isRegularFile(target)) {
            return WorktreeRollbackResult(false, "intent-to-add file is missing: $path")
        }
        val content = runCatching { Files.readString(target, StandardCharsets.UTF_8) }.getOrElse {
            return WorktreeRollbackResult(false, "intent-to-add content could not be inspected: $path")
        }
        val secretReport = credentialDiffGuard.inspectText(path, content)
        if (secretReport.changed) {
            recordTerritoryViolation(record, path, "intent_to_add_secret_content")
            return WorktreeRollbackResult(false, "intent-to-add refused secret-bearing staged content: $path")
        }
        val result = gitRunner.run(GitWorktreeOperation.INTENT_TO_ADD, record.worktreePath, path)
        return if (result.exitCode == 0) {
            WorktreeRollbackResult(true, "intent-to-add staged: $path")
        } else {
            WorktreeRollbackResult(false, "intent-to-add failed: ${redactionFilter.compact(result.output)}")
        }
    }

    fun rollback(worktreeId: String): WorktreeRollbackResult {
        val record = readRecord(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")

        try {
            // Restore baseline using git checkout
            gitRunner.run(GitWorktreeOperation.CHECKOUT_ALL, record.worktreePath)

            val revertedFiles = runCatching {
                gitRunner.run(GitWorktreeOperation.DIFF_NAME_ONLY, record.worktreePath).output
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()
            }.getOrDefault(emptyList())

            val updated = record.copy(rolledBack = true, appliedPatches = emptyList(), updatedAt = clock())
            writeRecord(updated)

            memoryStore.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.NOTE,
                title = "worktree rolled back: $worktreeId",
                body = "reverted ${revertedFiles.size} files",
                tags = listOf("worktree", "rollback"),
                subjectType = "worktree",
                subjectId = worktreeId
            )

            return WorktreeRollbackResult(true, "worktree $worktreeId rolled back", revertedFiles)
        } catch (e: Exception) {
            return WorktreeRollbackResult(false, "rollback failed: ${e.message}")
        }
    }

    fun verifyAndMerge(worktreeId: String, verificationCommand: String = "git diff --check"): WorktreeRollbackResult {
        val record = readRecord(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")

        if (verificationCommand.trim() != "git diff --check") {
            recordTerritoryViolation(record, "<verification-command>", "verification_command")
            return WorktreeRollbackResult(false, "verification command refused: only git diff --check is allowed")
        }

        try {
            // Verify
            val verifyResult = gitRunner.run(GitWorktreeOperation.DIFF_CHECK, record.worktreePath)
            if (verifyResult.exitCode != 0) {
                return WorktreeRollbackResult(false, "verification failed: ${verifyResult.output.take(200)}")
            }

            // Merge back using git diff and apply to main repo
            val diffResult = gitRunner.run(
                GitWorktreeOperation.DIFF_FROM_BASELINE,
                record.worktreePath,
                record.baselineCommit
            )
            val diff = diffResult.output
            if (diffResult.exitCode != 0) {
                return WorktreeRollbackResult(false, "could not inspect worktree diff")
            }

            if (diff.isBlank()) {
                return WorktreeRollbackResult(false, "verification refused: worktree produced no source diff")
            }

            val changed = extractPatchPaths(diff)
            val outside = firstOutsideTerritory(record, changed)
            if (outside != null) {
                recordTerritoryViolation(record, outside, "merge_apply")
                return WorktreeRollbackResult(false, "territory violation before merge: $outside")
            }
            val applyResult = gitRunner.run(GitWorktreeOperation.APPLY_PATCH, repoRoot, input = diff)
            if (applyResult.exitCode != 0) {
                return WorktreeRollbackResult(false, "merge apply failed")
            }

            val updated = record.copy(verified = true, mergedBack = true, updatedAt = clock())
            writeRecord(updated)

            // Clean up worktree
            runCatching {
                gitRunner.run(GitWorktreeOperation.WORKTREE_REMOVE, repoRoot, record.worktreePath.toString())
            }

            return WorktreeRollbackResult(true, "worktree $worktreeId verified and merged")
        } catch (e: Exception) {
            return WorktreeRollbackResult(false, "merge failed: ${e.message}")
        }
    }

    fun removeWorktree(worktreeId: String): Boolean {
        val record = readRecord(worktreeId) ?: return false
        runCatching { gitRunner.run(GitWorktreeOperation.WORKTREE_REMOVE, repoRoot, record.worktreePath.toString()) }
        Files.deleteIfExists(record.metaFile)
        return true
    }

    fun listWorktrees(): List<WorktreeRecord> {
        if (!Files.isDirectory(worktreeRoot)) return emptyList()
        val files = Files.list(worktreeRoot).use { stream -> stream.toList() }
        return files
            .filter { it.fileName.toString().endsWith(".meta") && it.fileName.toString().startsWith("wt-") }
            .mapNotNull { readRecordFromFile(it) }
            .sortedByDescending { it.createdAt }
    }

    fun readWorktree(worktreeId: String): WorktreeRecord? = readRecord(worktreeId)

    private fun firstOutsideTerritory(record: WorktreeRecord, paths: List<String>): String? {
        return atropos.core.territory.TerritoryEnforcer(record.territory).firstOutside(paths)
    }

    private fun extractPatchPaths(patchContent: String): List<String> =
        patchContent.lineSequence()
            .mapNotNull { line ->
                when {
                    line.startsWith("+++ b/") -> line.removePrefix("+++ b/")
                    line.startsWith("--- a/") -> line.removePrefix("--- a/")
                    line.startsWith("diff --git ") -> line.substringAfter(" b/", missingDelimiterValue = "")
                    else -> ""
                }.takeIf { it.isNotBlank() && it != "/dev/null" }
            }
            .map { it.trim() }
            .distinct()
            .toList()

    private fun isUnsafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank() || normalized == "/dev/null" || normalized.startsWith("/")) return true
        // Any traversal segment (even if resolved to a safe path) is disallowed.
        val segments = normalized.split("/")
        if (segments.any { it == ".." }) return true
        val parsed = runCatching { Path.of(normalized) }.getOrNull() ?: return true
        val canonical = parsed.normalize().toString().replace('\\', '/')
        return parsed.isAbsolute || canonical == ".." || canonical.startsWith("../")
    }

    private fun recordTerritoryViolation(record: WorktreeRecord, path: String, operation: String) {
        memoryStore.rememberFailure(
            subjectType = "territory_violation",
            subjectId = record.id,
            title = "territory violation: $operation",
            body = "worktree=${record.id} job=${record.jobId} path=$path territory=${record.territory.joinToString(",")}",
            tags = listOf("worktree", "territory", "denied", operation)
        )
    }

    private fun readRecord(worktreeId: String): WorktreeRecord? {
        val id = worktreeId.trim().removeSuffix(".meta")
        if (id.isBlank() || id.contains("/") || id.contains("\\")) return null
        val file = worktreeRoot.resolve("$id.meta").normalize()
        if (!file.startsWith(worktreeRoot) || !Files.isRegularFile(file)) return null
        return readRecordFromFile(file)
    }

    private fun readRecordFromFile(file: Path): WorktreeRecord? {
        val fields = runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        return runCatching {
            WorktreeRecord(
                id = fields["id"].orEmpty(),
                jobId = fields["jobId"].orEmpty(),
                worktreePath = Path.of(fields["worktreePath"].orEmpty()),
                baselineCommit = fields["baselineCommit"]?.takeIf { it.isNotBlank() },
                territory = fields["territory"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                dirtyEvidence = decode(fields["dirtyEvidenceB64"]).takeIf { it.isNotBlank() },
                appliedPatches = fields["appliedPatches"]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                verified = fields["verified"]?.toBooleanStrictOrNull() ?: false,
                rolledBack = fields["rolledBack"]?.toBooleanStrictOrNull() ?: false,
                mergedBack = fields["mergedBack"]?.toBooleanStrictOrNull() ?: false,
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                metaFile = file
            )
        }.getOrNull()
    }

    private fun writeRecord(record: WorktreeRecord) {
        Files.createDirectories(worktreeRoot)
        val tmp = Files.createTempFile(worktreeRoot, record.id, ".tmp")
        val content = buildString {
            appendLine("id=${record.id}")
            appendLine("jobId=${record.jobId}")
            appendLine("worktreePath=${record.worktreePath}")
            appendLine("baselineCommit=${record.baselineCommit ?: ""}")
            appendLine("territory=${record.territory.joinToString(",")}")
            appendLine("dirtyEvidenceB64=${encode(record.dirtyEvidence.orEmpty())}")
            appendLine("appliedPatches=${record.appliedPatches.joinToString("|")}")
            appendLine("verified=${record.verified}")
            appendLine("rolledBack=${record.rolledBack}")
            appendLine("mergedBack=${record.mergedBack}")
            appendLine("createdAt=${record.createdAt}")
            appendLine("updatedAt=${record.updatedAt}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, record.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
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
