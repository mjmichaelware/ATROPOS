package atropos.core.factory

import atropos.core.thinking.Thinking
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

class AppGitHelper(
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    fun runGit(directory: Path, operation: GitWorktreeOperation, argument: String? = null): String {
        val normalizedDir = directory.toAbsolutePath().normalize()
        val result = try {
            gitRunner.run(operation, normalizedDir, argument)
        } catch (failure: Exception) {
            Thinking.detail(
                "git_exec_failure",
                "git ${operation.name.lowercase()} threw in $normalizedDir: ${failure.message ?: failure.javaClass.simpleName}"
            )
            return ""
        }
        if (result.exitCode != 0) {
            Thinking.detail(
                "git_exec_failure",
                "git ${operation.name.lowercase()} failed (exit=${result.exitCode}): ${result.output.take(240)}"
            )
            return ""
        }
        return result.output
    }
}
