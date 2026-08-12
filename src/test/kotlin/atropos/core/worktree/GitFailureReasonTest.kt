/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.worktree

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A self-host run stalled on "merge apply failed" and nothing else. Git had
 * said which file and why; the code discarded it, the DAG node failed, the
 * compile gate never ran, and the verdict reported an unmet predicate with no
 * cause. These hold the fix: git's own words reach the operator.
 */
class GitFailureReasonTest {

    @Test
    fun `the reason carries git's first line and the exit code`() {
        val result = GitWorktreeCommandResult(
            exitCode = 1,
            output = "error: patch failed: src/main/kotlin/atropos/Main.kt:12\n" +
                "error: src/main/kotlin/atropos/Main.kt: patch does not apply"
        )

        val reason = result.failureReason("merge apply")

        assertTrue(reason.contains("merge apply failed (exit=1)"))
        assertTrue(reason.contains("patch failed: src/main/kotlin/atropos/Main.kt:12"))
    }

    @Test
    fun `a silent failure is itself reported as a symptom`() {
        val reason = GitWorktreeCommandResult(exitCode = 128, output = "").failureReason("reading the diff")

        assertTrue(reason.contains("exit=128"))
        assertTrue(reason.contains("no output"))
    }

    @Test
    fun `leading blank lines do not become the reason`() {
        val result = GitWorktreeCommandResult(exitCode = 1, output = "\n\n   \nfatal: not a git repository")

        assertTrue(result.failureReason("git status").contains("fatal: not a git repository"))
    }

    @Test
    fun `output is redacted before it reaches the operator`() {
        val result = GitWorktreeCommandResult(
            exitCode = 1,
            output = "error: patch failed: token sk-ant-api03-${"A".repeat(40)}"
        )

        val reason = result.failureReason("merge apply") { text ->
            atropos.core.security.RedactionFilter().redact(text)
        }

        assertFalse(
            reason.contains("sk-ant-api03-${"A".repeat(40)}"),
            "git error text quotes file contents, and file contents are eventually a credential"
        )
    }

    @Test
    fun `a very long git error is bounded`() {
        val result = GitWorktreeCommandResult(exitCode = 1, output = "e".repeat(5_000))

        assertTrue(result.failureReason("merge apply").length < 400)
    }

    @Test
    fun `a successful result is not a failure`() {
        assertTrue(GitWorktreeCommandResult(exitCode = 0, output = "fine").ok)
        assertFalse(GitWorktreeCommandResult(exitCode = 1, output = "bad").ok)
    }
}
