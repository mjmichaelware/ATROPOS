package atropos.core.agent

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/** Reads the changed paths for one bounded self-host mutation. */
class SelfHostWorktreeDiffInspector(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    fun diffFromBaseline(baselineCommit: String?, worktreeRoot: Path): String? {
        val result = gitRunner.run(
            GitWorktreeOperation.DIFF_FROM_BASELINE,
            worktreeRoot,
            baselineCommit
        )
        return result.output.takeIf { result.exitCode == 0 && it.isNotBlank() }
    }

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
}
