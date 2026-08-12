/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.verification.CompletionGateReport
import atropos.core.verification.GateResult
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The mutation boundary, which is the boundary that actually writes.
 *
 * Before this owner existed a self-host mutation was merged into the live tree
 * and marked COMPLETE after `git diff --check` alone, so uncompilable source
 * could land and stay. Every test here is a way that used to be possible.
 */
class SelfHostMutationVerificationGateTest {

    private fun repo(): Path {
        val root = Files.createTempDirectory("atropos-mutation-verify-")
        listOf(
            listOf("git", "init"),
            listOf("git", "config", "user.email", "atropos@example.invalid"),
            listOf("git", "config", "user.name", "ATROPOS Test")
        ).forEach { command ->
            ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start().waitFor()
        }
        Files.createDirectories(root.resolve("src"))
        Files.writeString(root.resolve("src/A.kt"), "class A\n", StandardCharsets.UTF_8)
        listOf(listOf("git", "add", "."), listOf("git", "commit", "-m", "initial")).forEach { command ->
            ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start().waitFor()
        }
        return root
    }

    private fun node() = DagNode(
        id = "node-mutate",
        label = "mutate",
        action = DagNodeAction.CREATE_FILE,
        actionPayload = "write src/B.kt",
        territory = listOf("src"),
        expectedOutputs = listOf("src/B.kt"),
        result = "done",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        metaFile = Path.of("unused")
    )

    private fun report(canComplete: Boolean, detail: String) = CompletionGateReport(
        nodeId = "node-mutate",
        canComplete = canComplete,
        gateResults = listOf(GateResult("node-mutate", canComplete, "Compile Gate", detail, Instant.now())),
        message = if (canComplete) "all gates passed" else "gates failed: Compile Gate: $detail"
    )

    /** A real patch that adds one file, so reversal has something to undo. */
    private fun addFileDiff(): String = """
        diff --git a/src/B.kt b/src/B.kt
        new file mode 100644
        index 0000000..6b2aaa7
        --- /dev/null
        +++ b/src/B.kt
        @@ -0,0 +1 @@
        +class B
    """.trimIndent() + "\n"

    private fun applyToTree(root: Path, diff: String) {
        val process = ProcessBuilder("git", "apply")
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(diff.toByteArray(StandardCharsets.UTF_8)) }
        process.waitFor()
    }

    // --- acceptance -------------------------------------------------------

    @Test
    fun a_mutation_that_passes_the_completion_gate_is_accepted_and_left_in_place() {
        val root = repo()
        applyToTree(root, addFileDiff())
        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            evaluate = { report(canComplete = true, detail = "compilation succeeded") }
        )

        val verdict = gate.verifyMerged(node(), addFileDiff())

        assertIs<SelfHostMutationVerdict.Accepted>(verdict)
        assertTrue(Files.exists(root.resolve("src/B.kt")), "an accepted mutation must survive")
        assertTrue(verdict.evidenceLine().contains("accepted=true"), verdict.evidenceLine())
    }

    // --- C1-SB-02 at the mutation boundary --------------------------------

    @Test
    fun a_nonzero_compile_exit_rejects_the_mutation_and_takes_it_back_out() {
        val root = repo()
        applyToTree(root, addFileDiff())
        assertTrue(Files.exists(root.resolve("src/B.kt")), "precondition: the change landed")

        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            evaluate = { report(canComplete = false, detail = "compilation failed (exit=1)") }
        )

        val verdict = gate.verifyMerged(node(), addFileDiff())

        val rejected = assertIs<SelfHostMutationVerdict.Rejected>(verdict)
        assertTrue(rejected.reason.contains("Compile Gate"), rejected.reason)
        assertTrue(rejected.reverted.ok, "the rejected change must be reversed: ${rejected.reverted.message}")
        assertFalse(rejected.treeIsDirty)
        assertFalse(
            Files.exists(root.resolve("src/B.kt")),
            "uncompilable source must not remain in the working tree"
        )
    }

    @Test
    fun the_real_gate_refuses_a_mutation_when_every_command_exits_nonzero() {
        // No injected report anywhere: the refusal comes from the real
        // VerifiedCompletionGate running against a failing process runner.
        val root = repo()
        applyToTree(root, addFileDiff())
        val alwaysFails = atropos.core.policy.BoundedProcessRunner { _, _, _, _ ->
            ProcessBuilder("false").start()
        }
        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(
                repoRoot = root,
                dagStore = atropos.core.dag.DagStore(root),
                processRunner = alwaysFails
            )
        )

        val verdict = gate.verifyMerged(node(), addFileDiff())

        val rejected = assertIs<SelfHostMutationVerdict.Rejected>(verdict)
        assertFalse(Files.exists(root.resolve("src/B.kt")), "the tree must be restored")
        assertTrue(rejected.reverted.ok, rejected.reverted.message)
    }

    // --- failure modes of the refusal itself ------------------------------

    @Test
    fun a_gate_that_crashes_is_a_refusal_not_a_pass() {
        val root = repo()
        applyToTree(root, addFileDiff())
        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            evaluate = { error("verifier exploded") }
        )

        val verdict = gate.verifyMerged(node(), addFileDiff())

        val rejected = assertIs<SelfHostMutationVerdict.Rejected>(verdict)
        assertTrue(rejected.reason.contains("crashed"), rejected.reason)
        assertFalse(Files.exists(root.resolve("src/B.kt")))
    }

    @Test
    fun a_reversal_that_fails_is_reported_as_a_dirty_tree_rather_than_swallowed() {
        val root = repo()
        // The patch was never applied, so reversing it cannot succeed. The
        // operator has to learn that, not be told the tree is clean.
        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            gitRunner = BoundedGitWorktreeCommandRunner(),
            evaluate = { report(canComplete = false, detail = "compilation failed") }
        )

        val verdict = gate.verifyMerged(node(), addFileDiff())

        val rejected = assertIs<SelfHostMutationVerdict.Rejected>(verdict)
        assertFalse(rejected.reverted.ok)
        assertTrue(rejected.treeIsDirty, "a failed reversal must surface as a dirty tree")
        assertTrue(rejected.evidenceLine().contains("dirty=true"), rejected.evidenceLine())
    }

    @Test
    fun an_empty_diff_cannot_be_reversed_and_says_so() {
        val root = repo()
        val gate = SelfHostMutationVerificationGate(
            repoRoot = root,
            completionGate = VerifiedCompletionGate(repoRoot = root),
            evaluate = { report(canComplete = false, detail = "compilation failed") }
        )

        val verdict = gate.verifyMerged(node(), mergedDiff = "")

        val rejected = assertIs<SelfHostMutationVerdict.Rejected>(verdict)
        assertFalse(rejected.reverted.ok)
        assertEquals("no recorded diff to reverse", rejected.reverted.message)
    }
}
