package atropos.core.provider

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeCommandResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GitRepositoryMetadataReaderTest {
    @Test
    fun reads_branch_ref_and_baseline_hash_from_active_repository_root() {
        val root = Files.createTempDirectory("atropos-context-root-")
        val directories = mutableListOf<java.nio.file.Path>()
        val runner = BoundedGitWorktreeCommandRunner { command, directory, _ ->
            directories.add(directory)
            when (command.drop(1)) {
                listOf("rev-parse", "--abbrev-ref", "HEAD") ->
                    GitWorktreeCommandResult(0, "feature/self-host\n")
                listOf("rev-parse", "HEAD") ->
                    GitWorktreeCommandResult(0, "0123456789abcdef\n")
                else -> error("unexpected command: $command")
            }
        }

        val reader = GitRepositoryMetadataReader(runner)
        val branch = reader.readBranch(root)
        val commit = reader.readBaselineCommit(root)

        assertEquals("feature/self-host", branch.value)
        assertNull(branch.failure)
        assertEquals("0123456789abcdef", commit.value)
        assertNull(commit.failure)
        assertEquals(listOf(root, root), directories)
    }

    @Test
    fun uses_deterministic_fallback_and_typed_failure_for_unavailable_metadata() {
        val root = Files.createTempDirectory("atropos-context-missing-")
        val reader = GitRepositoryMetadataReader(
            gitRunner = BoundedGitWorktreeCommandRunner { command, _, _ ->
                if (command.drop(1) == listOf("rev-parse", "HEAD")) {
                    GitWorktreeCommandResult(128, "secret remote output")
                } else {
                    GitWorktreeCommandResult(0, "")
                }
            },
            fallback = "unknown"
        )

        val branch = reader.readBranch(root)
        val commit = reader.readBaselineCommit(root)

        assertEquals("unknown", branch.value)
        assertNotNull(branch.failure)
        assertEquals(GitMetadataField.BRANCH, branch.failure?.field)
        assertEquals("empty_metadata", branch.failure?.reason)
        assertEquals("unknown", commit.value)
        assertNotNull(commit.failure)
        assertEquals(GitMetadataField.BASELINE_COMMIT, commit.failure?.field)
        assertEquals("git_command_failed", commit.failure?.reason)
    }
}
