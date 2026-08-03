package atropos.core.agent

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/** Reads the changed paths for one bounded self-host mutation. */
class SelfHostWorktreeDiffInspector(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    fun changedPaths(baselineCommit: String?, worktreeRoot: Path, path: Path): List<String> {
        val result = gitRunner.run(
            GitWorktreeOperation.DIFF_NAME_ONLY,
            worktreeRoot,
            baselineCommit,
            path.toString()
        )
        if (result.exitCode != 0) return emptyList()
        return result.output.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    }

    /**
     * The full patch text for the mutation, captured before it is merged.
     *
     * Needed because the merge removes the worktree that produced it. Without the
     * patch in hand there is no way to undo a change that later fails
     * verification — the source of truth for the reversal is gone.
     */
    fun unifiedDiff(baselineCommit: String?, worktreeRoot: Path): String {
        val result = runCatching {
            gitRunner.run(GitWorktreeOperation.DIFF_FROM_BASELINE, worktreeRoot, baselineCommit)
        }.getOrNull() ?: return ""
        return if (result.exitCode == 0) result.output else ""
    }
}
