package atropos.core.agent

import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeCommandResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfHostGitBaselineReaderTest {
    @Test
    fun baseline_reader_uses_typed_git_operations_and_fails_closed_on_nonzero_exit() {
        val calls = mutableListOf<List<String>>()
        val runner = BoundedGitWorktreeCommandRunner { command, _, _ ->
            calls += command
            when (command.drop(1)) {
                listOf("rev-parse", "HEAD") -> GitWorktreeCommandResult(0, "abc123\n")
                listOf("status", "--porcelain") -> GitWorktreeCommandResult(1, "git failed")
                else -> error("unexpected command: $command")
            }
        }

        val baseline = SelfHostGitBaselineReader(Files.createTempDirectory("atropos-baseline-"), runner)
            .read { value -> "fingerprint:${value.length}" }

        assertEquals("abc123", baseline.commit)
        assertNull(baseline.dirtyFingerprint)
        assertEquals(
            listOf(
                listOf("git", "rev-parse", "HEAD"),
                listOf("git", "status", "--porcelain")
            ),
            calls
        )
    }
}
