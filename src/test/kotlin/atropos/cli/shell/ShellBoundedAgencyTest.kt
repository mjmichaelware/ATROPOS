/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.shell

import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ExecutionPolicyRequest
import atropos.core.policy.PolicyDecisionType
import atropos.core.policy.TypedToolExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 10 Batch 1 — the shell path must route every side effect through
 * bounded agency, and a proposal the system did not authorise must never reach
 * a process.
 */
class ShellBoundedAgencyTest {

    /** Counts spawns so "never reached ProcessBuilder" is an observation, not a hope. */
    private class SpawnCounter {
        val count = AtomicInteger(0)
        val seam: (List<String>, File) -> Process = { _, _ ->
            count.incrementAndGet()
            error("bounded agency leaked: a refused proposal reached the process seam")
        }
    }

    /** Forces one disposition so the caller's handling of it can be exercised. */
    private class FixedDecisionEngine(
        repoRoot: Path,
        private val decision: PolicyDecisionType,
        private val reason: String
    ) : ExecutionPolicyEngine(repoRoot) {
        override fun evaluate(request: ExecutionPolicyRequest): ExecutionPolicyDecision =
            ExecutionPolicyDecision(
                id = "test-decision",
                decision = decision,
                actionClass = request.actionClass,
                destructive = false,
                reason = reason
            )
    }

    private fun runnerFor(
        decision: PolicyDecisionType,
        reason: String,
        spawnCounter: SpawnCounter
    ): ShellCommandRunner {
        val repoRoot = Files.createTempDirectory("atropos-shell-agency-")
        return ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(
                BoundedAgencyGate(FixedDecisionEngine(repoRoot, decision, reason))
            ),
            spawn = spawnCounter.seam
        )
    }

    @Test
    fun policy_blocked_proposal_never_reaches_the_process_seam() {
        val spawns = SpawnCounter()
        val result = runnerFor(PolicyDecisionType.DENY, "destructive shell command refused", spawns)
            .run(listOf("echo", "BOUNDED_AGENCY_LEAK"))

        assertEquals(0, spawns.count.get(), "a blocked proposal must not spawn a process")
        assertEquals(AgencyDisposition.POLICY_BLOCKED, result.disposition)
        assertEquals(126, result.exitCode)
        assertTrue(!result.passed)
        assertTrue(
            !result.output.contains("BOUNDED_AGENCY_LEAK"),
            "refusal output must not contain command output"
        )
    }

    @Test
    fun approval_required_proposal_never_reaches_the_process_seam() {
        val spawns = SpawnCounter()
        val result = runnerFor(
            PolicyDecisionType.APPROVAL_REQUIRED,
            "network action requires explicit integration ownership",
            spawns
        ).run(listOf("echo", "BOUNDED_AGENCY_LEAK"))

        assertEquals(0, spawns.count.get(), "an unapproved proposal must not spawn a process")
        assertEquals(AgencyDisposition.APPROVAL_REQUIRED, result.disposition)
        assertTrue(!result.passed)
    }

    @Test
    fun approval_required_is_not_collapsed_into_policy_blocked() {
        val blocked = runnerFor(PolicyDecisionType.DENY, "denied", SpawnCounter())
            .run(listOf("echo", "hi"))
        val pending = runnerFor(PolicyDecisionType.APPROVAL_REQUIRED, "needs approval", SpawnCounter())
            .run(listOf("echo", "hi"))

        assertTrue(
            blocked.exitCode != pending.exitCode,
            "approval-required must be distinguishable from policy-blocked"
        )
        assertEquals(126, blocked.exitCode)
        assertEquals(125, pending.exitCode)
        assertTrue(blocked.disposition != pending.disposition)
    }

    @Test
    fun refusal_is_a_typed_outcome_a_compositor_can_render() {
        val runner = runnerFor(
            PolicyDecisionType.APPROVAL_REQUIRED,
            "network action requires explicit integration ownership",
            SpawnCounter()
        )
        val result = runner.run(listOf("echo", "hi"))

        assertNotNull(result.disposition, "disposition must survive to the caller")
        assertNotNull(result.proposalId, "the proposal must be identifiable for a later approval")
        assertEquals("network action requires explicit integration ownership", result.policyReason)

        val rendered = runner.render(result)
        assertTrue(rendered.contains("disposition: approval_required"), rendered)
        assertTrue(rendered.contains("proposal: "), rendered)
    }

    @Test
    fun allowed_proposal_reaches_the_typed_executor() {
        val repoRoot = Files.createTempDirectory("atropos-shell-agency-allow-")
        val runner = ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)))
        )

        val result = runner.run(listOf("echo", "bounded-agency-ok"))

        assertEquals(AgencyDisposition.ALLOWED, result.disposition)
        assertNotNull(result.proposalId)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("bounded-agency-ok"), result.output)
        // An allowed command renders exactly as it did before this batch.
        assertTrue(!runner.render(result).contains("disposition:"))
    }

    @Test
    fun real_policy_verdicts_are_unchanged_by_the_gate() {
        val repoRoot = Files.createTempDirectory("atropos-shell-agency-parity-")
        val runner = ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))),
            spawn = SpawnCounter().seam
        )

        // Still denied, by the same engine rules, through the new chokepoint.
        val chained = runner.run(listOf("ls", "&&", "rm"))
        assertEquals(AgencyDisposition.POLICY_BLOCKED, chained.disposition)
        assertEquals(126, chained.exitCode)

        val gitMutation = runner.run(listOf("git", "push"))
        assertEquals(AgencyDisposition.POLICY_BLOCKED, gitMutation.disposition)
        assertEquals(126, gitMutation.exitCode)
    }

    @Test
    fun git_status_uses_the_bounded_executor_with_literal_argv() {
        val repoRoot = Files.createTempDirectory("atropos-git-status-argv-")
        var observed: List<String> = emptyList()
        val runner = ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(BoundedAgencyGate(FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "test allow"))),
            spawn = { command, _ ->
                observed = command
                error("test spawn seam")
            }
        )

        runner.gitStatus()

        assertEquals(listOf("git", "status", "--short"), observed)
    }

    @Test
    fun git_diff_uses_the_bounded_executor_with_literal_argv_and_path_boundary() {
        val repoRoot = Files.createTempDirectory("atropos-git-diff-argv-")
        var observed: List<String> = emptyList()
        val runner = ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(BoundedAgencyGate(FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "test allow"))),
            spawn = { command, _ ->
                observed = command
                error("test spawn seam")
            }
        )

        runner.gitDiff()

        assertEquals(listOf("git", "diff", "--"), observed)
    }

    @Test
    fun confirmed_git_mutation_uses_repository_scoped_file_mutation_argv() {
        val repoRoot = Files.createTempDirectory("atropos-git-mutation-argv-")
        var observed: List<String> = emptyList()
        val runner = ShellCommandRunner(
            initialDirectory = repoRoot,
            agency = TypedToolExecutor(BoundedAgencyGate(FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "test allow"))),
            spawn = { command, _ ->
                observed = command
                error("test spawn seam")
            }
        )

        runner.runGitMutation(listOf("git", "add", "--", "src/Main.kt"), listOf("src/Main.kt"))

        assertEquals(listOf("git", "add", "--", "src/Main.kt"), observed)
    }
}
