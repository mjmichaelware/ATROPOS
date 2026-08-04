/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeCommandResult
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/** Identifies which repository metadata field could not be read. */
enum class GitMetadataField {
    BRANCH,
    BASELINE_COMMIT
}

/** Typed, redacted failure for a bounded repository metadata read. */
data class GitMetadataFailure(
    val field: GitMetadataField,
    val exitCode: Int,
    val reason: String
)

data class GitMetadataValue(
    val value: String,
    val failure: GitMetadataFailure? = null
)

/**
 * Reads only the Git metadata needed for context attestation.
 *
 * This composes the existing typed Git process owner. It never exposes
 * command output in failures, and keeps the envelope's historical fallback
 * value when a repository is unavailable or metadata is malformed.
 */
class GitRepositoryMetadataReader(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val fallback: String = "unknown"
) {
    fun readBranch(repoRoot: Path): GitMetadataValue =
        read(repoRoot, GitWorktreeOperation.REV_PARSE_BRANCH, GitMetadataField.BRANCH)

    fun readBaselineCommit(repoRoot: Path): GitMetadataValue =
        read(repoRoot, GitWorktreeOperation.REV_PARSE_HEAD, GitMetadataField.BASELINE_COMMIT)

    private fun read(
        repoRoot: Path,
        operation: GitWorktreeOperation,
        field: GitMetadataField
    ): GitMetadataValue {
        val result = gitRunner.run(operation, repoRoot)
        val value = result.output.trim()
        if (result.exitCode == 0 && value.isNotBlank() && !value.contains('\n')) {
            return GitMetadataValue(value)
        }
        return GitMetadataValue(
            value = fallback,
            failure = GitMetadataFailure(
                field = field,
                exitCode = result.exitCode,
                reason = failureReason(result)
            )
        )
    }

    private fun failureReason(result: GitWorktreeCommandResult): String =
        when {
            result.exitCode != 0 -> "git_command_failed"
            result.output.isBlank() -> "empty_metadata"
            else -> "malformed_metadata"
        }
}
