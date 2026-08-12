package atropos.core.worktree

import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.CredentialDiffGuard
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class IsolatedWorktreeService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val credentialDiffGuard: CredentialDiffGuard = CredentialDiffGuard(),
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    private val recordStore = WorktreeRecordStore(repoRoot.resolve(".atropos/worktrees").normalize())
    private val worktreeRoot = recordStore.root()

    fun createWorktree(jobId: String, territory: List<String> = emptyList()): WorktreeCreateResult {
        try {
            recordStore.ensureRoot()

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
                    worktreeOutput.failureReason("creating the worktree", redactionFilter::compact)
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
            recordStore.write(record)

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
        val record = recordStore.read(worktreeId) ?: return false
        if (patchContent.isBlank()) {
            recordTerritoryViolation(record, "<empty-patch>", "patch_apply_empty")
            return false
        }
        val patchPaths = WorktreePatchPaths.extract(patchContent)
        val unsafePath = WorktreePatchPaths.firstUnsafe(patchPaths)
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
                recordStore.write(updated)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Writes one non-empty, territory-bound file in an existing worktree. */
    fun writeFile(worktreeId: String, relativePath: String, content: String): Boolean {
        val record = recordStore.read(worktreeId) ?: return false
        val path = relativePath.trim()
        if (content.isBlank()) {
            recordTerritoryViolation(record, path.ifBlank { "<empty-path>" }, "file_write_empty")
            return false
        }
        if (WorktreePatchPaths.isUnsafeRelativePath(path)) {
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
        val record = recordStore.read(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")
        val path = relativePath.trim()
        if (WorktreePatchPaths.isUnsafeRelativePath(path)) {
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
            WorktreeRollbackResult(false, result.failureReason("intent-to-add", redactionFilter::compact))
        }
    }

    fun rollback(worktreeId: String): WorktreeRollbackResult {
        val record = recordStore.read(worktreeId)
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
            recordStore.write(updated)

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
        val record = recordStore.read(worktreeId)
            ?: return WorktreeRollbackResult(false, "worktree not found: $worktreeId")

        if (verificationCommand.trim() != "git diff --check") {
            recordTerritoryViolation(record, "<verification-command>", "verification_command")
            return WorktreeRollbackResult(false, "verification command refused: only git diff --check is allowed")
        }

        try {
            // Verify
            val verifyResult = gitRunner.run(GitWorktreeOperation.DIFF_CHECK, record.worktreePath)
            if (verifyResult.exitCode != 0) {
                return WorktreeRollbackResult(
                    false,
                    verifyResult.failureReason("git diff --check", redactionFilter::compact)
                )
            }

            // Merge back using git diff and apply to main repo
            val diffResult = gitRunner.run(
                GitWorktreeOperation.DIFF_FROM_BASELINE,
                record.worktreePath,
                record.baselineCommit
            )
            val diff = diffResult.output
            if (diffResult.exitCode != 0) {
                return WorktreeRollbackResult(
                    false,
                    diffResult.failureReason("reading the worktree diff", redactionFilter::compact)
                )
            }

            if (diff.isBlank()) {
                return WorktreeRollbackResult(false, "verification refused: worktree produced no source diff")
            }

            val changed = WorktreePatchPaths.extract(diff)
            val outside = firstOutsideTerritory(record, changed)
            if (outside != null) {
                recordTerritoryViolation(record, outside, "merge_apply")
                return WorktreeRollbackResult(false, "territory violation before merge: $outside")
            }
            val applyResult = gitRunner.run(GitWorktreeOperation.APPLY_PATCH, repoRoot, input = diff)
            if (applyResult.exitCode != 0) {
                // git says exactly why an apply failed -- the file, the hunk,
                // whether the tree was already dirty. Discarding that left the
                // operator with "merge apply failed" and no way forward, which
                // is where a self-host run silently stalls: the node fails, the
                // compile gate never runs, and the verdict reports an unmet
                // predicate without the cause.
                val reason = applyResult.output.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: "git apply exited ${applyResult.exitCode} with no output"

                val remedy = if (changed.any { path -> workingTreeAlreadyModified(path) }) {
                    " — ${changed.size} target path(s) are already modified in the working tree; " +
                        "revert or commit them, then re-run"
                } else {
                    ""
                }
                return WorktreeRollbackResult(false, "merge apply failed: $reason$remedy")
            }

            val updated = record.copy(verified = true, mergedBack = true, updatedAt = clock())
            recordStore.write(updated)

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
        val record = recordStore.read(worktreeId) ?: return false
        runCatching { gitRunner.run(GitWorktreeOperation.WORKTREE_REMOVE, repoRoot, record.worktreePath.toString()) }
        recordStore.delete(record)
        return true
    }

    fun listWorktrees(): List<WorktreeRecord> = recordStore.list()

    fun readWorktree(worktreeId: String): WorktreeRecord? = recordStore.read(worktreeId)

    /**
     * Whether the real tree already carries changes to this path.
     *
     * The commonest cause of a failed apply, and the one an operator cannot
     * guess: a previous run left the target files modified, so the next run's
     * diff no longer applies cleanly and every run after that fails the same
     * way. Naming it turns a permanent stall into one `git checkout`.
     */
    private fun workingTreeAlreadyModified(path: String): Boolean {
        val status = runCatching {
            gitRunner.run(GitWorktreeOperation.STATUS_PORCELAIN, repoRoot)
        }.getOrNull() ?: return false
        if (status.exitCode != 0) return false
        return status.output.lineSequence().any { line ->
            line.length > 3 && line.substring(3).trim().endsWith(path.substringAfterLast('/'))
        }
    }

    private fun firstOutsideTerritory(record: WorktreeRecord, paths: List<String>): String? {
        return atropos.core.territory.TerritoryEnforcer(record.territory).firstOutside(paths)
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
}
