package atropos.core.worktree

import atropos.core.memory.LocalMemoryStore
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
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val worktreeRoot = repoRoot.resolve(".atropos/worktrees").normalize()

    fun createWorktree(jobId: String, territory: List<String> = emptyList()): WorktreeCreateResult {
        try {
            Files.createDirectories(worktreeRoot)

            // Capture baseline commit
            val baselineCommit = runCatching {
                val proc = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().readText().trim()
            }.getOrNull()

            // Capture dirty state
            val dirtyEvidence = runCatching {
                val proc = ProcessBuilder("git", "status", "--porcelain")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                proc.inputStream.bufferedReader().readText().trim()
            }.getOrNull()

            val id = "wt-" + UUID.randomUUID().toString().take(12)
            val wtDir = worktreeRoot.resolve(id)
            Files.createDirectories(wtDir)

            // Create worktree using git worktree
            runCatching {
                ProcessBuilder("git", "worktree", "add", "--detach", wtDir.toString(), baselineCommit ?: "HEAD")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
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
        return runCatching {
            val proc = ProcessBuilder("sh", "-c", "git apply")
                .directory(record.worktreePath.toFile())
                .redirectErrorStream(true)
                .start()
            proc.outputStream.write(patchContent.toByteArray(StandardCharsets.UTF_8))
            proc.outputStream.close()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
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

    fun rollback(worktreeId: String): WorktreeRollbackResult {
        val record = readRecord(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")

        try {
            // Restore baseline using git checkout
            val proc = ProcessBuilder("git", "checkout", "--", ".")
                .directory(record.worktreePath.toFile())
                .redirectErrorStream(true)
                .start()
            proc.waitFor()

            val revertedFiles = runCatching {
                val status = ProcessBuilder("git", "diff", "--name-only")
                    .directory(record.worktreePath.toFile())
                    .redirectErrorStream(true)
                    .start()
                status.inputStream.bufferedReader().readText()
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

        try {
            // Verify
            val verifyProc = ProcessBuilder("sh", "-c", verificationCommand)
                .directory(record.worktreePath.toFile())
                .redirectErrorStream(true)
                .start()
            val verifyOutput = verifyProc.inputStream.bufferedReader().readText()
            val verifyExit = verifyProc.waitFor()
            if (verifyExit != 0) {
                return WorktreeRollbackResult(false, "verification failed: ${verifyOutput.take(200)}")
            }

            // Merge back using git diff and apply to main repo
            val diffProc = ProcessBuilder("git", "diff", record.baselineCommit ?: "HEAD", "--", ".")
                .directory(record.worktreePath.toFile())
                .redirectErrorStream(true)
                .start()
            val diff = diffProc.inputStream.bufferedReader().readText()
            diffProc.waitFor()

            if (diff.isNotBlank()) {
                val applyProc = ProcessBuilder("sh", "-c", "git apply")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
                applyProc.outputStream.write(diff.toByteArray(StandardCharsets.UTF_8))
                applyProc.outputStream.close()
                val applyExit = applyProc.waitFor()
                if (applyExit != 0) {
                    return WorktreeRollbackResult(false, "merge apply failed")
                }
            }

            val updated = record.copy(verified = true, mergedBack = true, updatedAt = clock())
            writeRecord(updated)

            // Clean up worktree
            runCatching {
                ProcessBuilder("git", "worktree", "remove", "--force", record.worktreePath.toString())
                    .directory(repoRoot.toFile())
                    .start()
                    .waitFor()
            }

            return WorktreeRollbackResult(true, "worktree $worktreeId verified and merged")
        } catch (e: Exception) {
            return WorktreeRollbackResult(false, "merge failed: ${e.message}")
        }
    }

    fun removeWorktree(worktreeId: String): Boolean {
        val record = readRecord(worktreeId) ?: return false
        runCatching {
            ProcessBuilder("git", "worktree", "remove", "--force", record.worktreePath.toString())
                .directory(repoRoot.toFile())
                .start()
                .waitFor()
        }
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
