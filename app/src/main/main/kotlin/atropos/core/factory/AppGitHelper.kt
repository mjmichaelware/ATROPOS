package atropos.core.factory

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

class AppGitHelper(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    fun runGit(directory: Path, operation: GitWorktreeOperation, argument: String? = null): String {
        val result = gitRunner.run(operation, directory, argument)
        check(result.exitCode == 0) { "app git command failed: ${operation.name.lowercase()}: ${result.output.take(240)}" }
        return result.output
    }
}
