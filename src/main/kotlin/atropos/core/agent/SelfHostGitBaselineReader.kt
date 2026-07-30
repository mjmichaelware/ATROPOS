package atropos.core.agent

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/** Reads the immutable Git baseline used to make a self-host goal durable. */
class SelfHostGitBaselineReader(
    private val repoRoot: Path,
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner()
) {
    data class Baseline(
        val commit: String?,
        val dirtyFingerprint: String?
    )

    fun read(fingerprint: (String) -> String): Baseline = Baseline(
        commit = gitRunner.run(GitWorktreeOperation.REV_PARSE_HEAD, repoRoot)
            .takeIfSuccessful()
            ?.trim(),
        dirtyFingerprint = gitRunner.run(GitWorktreeOperation.STATUS_PORCELAIN, repoRoot)
            .takeIfSuccessful()
            ?.let(fingerprint)
    )

    private fun atropos.core.worktree.GitWorktreeCommandResult.takeIfSuccessful(): String? =
        output.takeIf { exitCode == 0 }
}
