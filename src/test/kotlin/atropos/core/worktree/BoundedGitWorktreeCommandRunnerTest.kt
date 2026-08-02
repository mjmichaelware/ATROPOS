package atropos.core.worktree

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BoundedGitWorktreeCommandRunnerTest {
    @Test
    fun diff_operation_builds_only_allowlisted_argv() {
        val captured = mutableListOf<List<String>>()
        val runner = BoundedGitWorktreeCommandRunner { command, _, _ ->
            captured += command
            GitWorktreeCommandResult(0, "src/main.kt\n")
        }

        runner.run(
            GitWorktreeOperation.DIFF_NAME_ONLY,
            Files.createTempDirectory("atropos-bounded-git-"),
            "abc123",
            "src/main.kt"
        )

        assertEquals(
            listOf("git", "diff", "--name-only", "abc123", "--", "src/main.kt"),
            captured.single()
        )
    }

    @Test
    fun arbitrary_shell_text_and_traversal_are_not_accepted_as_operations() {
        val runner = BoundedGitWorktreeCommandRunner { _, _, _ ->
            error("process runner must not be reached")
        }
        val root = Files.createTempDirectory("atropos-bounded-git-refusal-")

        assertFailsWith<IllegalArgumentException> {
            runner.run(GitWorktreeOperation.INTENT_TO_ADD, root, "src/../escape.kt")
        }
        assertFailsWith<IllegalArgumentException> {
            runner.run(GitWorktreeOperation.DIFF_NAME_ONLY, root, "HEAD; touch marker", "src/main.kt")
        }
    }

    @Test
    fun no_input_git_operation_receives_eof_and_completes() {
        val root = Files.createTempDirectory("atropos-bounded-git-eof-")
        ProcessBuilder("git", "init", "--quiet", root.toString()).start().also {
            assertTrue(it.waitFor() == 0)
        }
        val result = BoundedGitWorktreeCommandRunner().run(
            GitWorktreeOperation.STATUS_PORCELAIN,
            root
        )

        assertEquals(0, result.exitCode)
    }
}
