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

    @Test
    fun app_history_operations_are_typed_and_commit_messages_are_bounded() {
        val captured = mutableListOf<List<String>>()
        val runner = BoundedGitWorktreeCommandRunner { command, _, _ ->
            captured += command
            GitWorktreeCommandResult(0, "")
        }
        val root = Files.createTempDirectory("atropos-bounded-git-app-")

        runner.run(GitWorktreeOperation.INIT, root)
        runner.run(GitWorktreeOperation.ADD_ALL, root)
        runner.run(GitWorktreeOperation.COMMIT, root, "initial app scaffold")
        runner.run(GitWorktreeOperation.ARCHIVE, root, root.resolve("app.tar").toString())

        assertEquals(listOf("git", "init"), captured[0])
        assertEquals(listOf("git", "add", "."), captured[1])
        assertTrue(captured[2].containsAll(listOf("commit", "-m", "initial app scaffold")))
        assertTrue(captured[3].contains("--format=tar"))
        assertFailsWith<IllegalArgumentException> {
            runner.run(GitWorktreeOperation.COMMIT, root, "bad\nmessage")
        }
    }

    @Test
    fun branch_creation_is_typed_and_rejects_shell_injection_shapes() {
        val captured = mutableListOf<List<String>>()
        val runner = BoundedGitWorktreeCommandRunner { command, _, _ ->
            captured += command
            GitWorktreeCommandResult(0, "")
        }
        val root = Files.createTempDirectory("atropos-bounded-git-branch-")
        runner.run(GitWorktreeOperation.CHECKOUT_BRANCH, root, "calculator-factory-1")
        assertEquals(listOf("git", "checkout", "-b", "calculator-factory-1"), captured.single())
        assertFailsWith<IllegalArgumentException> {
            runner.run(GitWorktreeOperation.CHECKOUT_BRANCH, root, "MusicMakerLM;touch-pwned")
        }
    }
}
