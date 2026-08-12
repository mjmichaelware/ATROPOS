package atropos.core.worktree

import java.nio.file.Path
import java.time.Instant

/**
 * The durable record of one isolated worktree.
 *
 * Every field exists to answer a question after the fact, when the worktree
 * directory may already be gone:
 *
 * @param baselineCommit what the worktree was cut from, so a diff has something
 *   to be taken against and a rollback has somewhere to return to.
 * @param territory the paths this worktree was allowed to touch. Recorded at
 *   creation rather than consulted from live config, so a later config change
 *   cannot retroactively authorise a write that was out of bounds when it happened.
 * @param dirtyEvidence what was already uncommitted in the repository when the
 *   worktree was created. Without it, pre-existing local changes could be
 *   mistaken for the worktree's own work at merge time.
 * @param mergedBack whether the change reached the real tree. Distinct from
 *   [verified]: a change can pass verification and still not be merged.
 */
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

/**
 * @param record null whenever [ok] is false — a failed creation never yields a
 *   half-built record, so no caller can proceed on a worktree that does not exist.
 */
data class WorktreeCreateResult(
    val ok: Boolean,
    val message: String,
    val record: WorktreeRecord? = null
)

/**
 * @param revertedFiles what was actually put back, so a rollback claim can be
 *   checked rather than trusted.
 */
data class WorktreeRollbackResult(
    val ok: Boolean,
    val message: String,
    val revertedFiles: List<String> = emptyList()
)
