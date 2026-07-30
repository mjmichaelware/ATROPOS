/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ExecutionPolicyRequest
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import atropos.core.policy.VerificationActionProposals
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 10 Batch 4 — a smoke command arrives as free text, so it is the closest
 * thing in the tree to raw provider prose reaching a process. Nothing may run
 * unless the system authorised it.
 *
 * There is no spawn seam here (the spawn block scrubs secret-bearing
 * environment variables and is deliberately left untouched), so "nothing ran"
 * is proven observationally: a refused command that *would* have produced
 * output comes back with no exit code and no stdout.
 */
class AgentSmokeBoundedAgencyTest {

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

    private fun repo(): Path = Files.createTempDirectory("atropos-smoke-agency-")

    @Test
    fun a_refused_smoke_command_never_runs() {
        val repoRoot = repo()
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.DENY, "smoke command refused")
            )
        )

        // `printf` passes validate() and would print if it ran, so empty
        // stdout is evidence the gate stopped it rather than validation.
        val result = runner.run("printf BOUNDED_AGENCY_LEAK")

        assertTrue(!result.passed)
        assertEquals("smoke command refused", result.refusalReason)
        assertNull(result.exitCode, "a refused smoke command must not produce an exit code")
        assertEquals("", result.stdout)
        assertTrue(!result.stdout.contains("BOUNDED_AGENCY_LEAK"))
    }

    @Test
    fun an_unapproved_smoke_command_never_runs() {
        val repoRoot = repo()
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.APPROVAL_REQUIRED, "needs approval")
            )
        )

        val result = runner.run("printf BOUNDED_AGENCY_LEAK")

        assertTrue(!result.passed)
        assertEquals("needs approval", result.refusalReason)
        assertNull(result.exitCode, "an unapproved smoke command must not produce an exit code")
        assertEquals("", result.stdout)
    }

    @Test
    fun syntactic_validation_still_refuses_before_any_proposal() {
        val repoRoot = repo()
        // An engine that allows everything: if validate() were skipped, a
        // chained command would reach the gate and be permitted.
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "allowed")
            )
        )

        val result = runner.run("printf a && rm -rf /")

        assertTrue(!result.passed)
        assertEquals("smoke command refuses shell chaining or redirects", result.refusalReason)
        assertNull(result.exitCode)
    }

    @Test
    fun network_smoke_commands_stay_refused_under_real_policy() {
        val repoRoot = repo()
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
        )

        // Refused by validate() before a proposal is even built. The engine
        // would also deny it; the point is that both layers still stand.
        val result = runner.run("curl http://example.invalid")

        assertTrue(!result.passed, "network smoke commands must stay refused")
        assertEquals("smoke command refuses dangerous operations", result.refusalReason)
        assertNull(result.exitCode)
    }

    @Test
    fun smoke_paths_cannot_escape_bounded_repository_root() {
        val repoRoot = repo()
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "allowed")
            )
        )

        val result = runner.run("cat ../outside-secret")

        assertTrue(!result.passed)
        assertEquals(AgentExecutionFailure.INVALID_COMMAND, result.failure)
        assertNull(result.exitCode)
    }

    @Test
    fun smoke_uses_shared_runner_and_preserves_nonzero_failure() {
        val repoRoot = repo()
        val runner = AgentSmokeRunner(
            repoRoot = repoRoot,
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.ALLOW, "allowed")
            ),
            processRunner = BoundedProcessRunner { command, _, _, _ ->
                assertEquals(listOf("false"), command)
                ProcessBuilder("false").start()
            }
        )

        val result = runner.run("false")

        assertTrue(!result.passed)
        assertEquals(AgentExecutionFailure.NONZERO_EXIT, result.failure)
    }

    @Test
    fun a_refused_verification_never_launches_gradle() {
        val repoRoot = repo()
        val collector = AgentContextCollector(repoRoot)
        val patchStore = AgentPatchStore(repoRoot)
        val patch = patchStore.createRecord(
            provider = "test",
            task = "bounded agency",
            contextBytes = 0,
            diff = "--- a/notes.txt\n+++ b/notes.txt\n@@ -1 +1 @@\n-old\n+new\n"
        )

        val verifier = AgentVerifier(
            collector = collector,
            patchStore = patchStore,
            verificationStore = AgentVerificationStore(repoRoot),
            agencyGate = BoundedAgencyGate(
                FixedDecisionEngine(repoRoot, PolicyDecisionType.DENY, "build/test command refused")
            )
        )

        val result = verifier.verify(patch.id)

        assertTrue(!result.passed)
        assertNull(result.exitCode, "a refused verification must not produce an exit code")
        assertEquals("build/test command refused", result.refusalReason)
        assertEquals("", result.stdout)
    }

    @Test
    fun proposals_reproduce_the_previous_policy_requests() {
        val repoRoot = repo()
        val engine = ExecutionPolicyEngine(repoRoot)

        val build = VerificationActionProposals.buildTest(
            listOf("./gradlew", "test", "jar", "--no-daemon"),
            repoRoot,
            ActionActor.HierarchyNode("verify", "p1")
        )
        assertEquals(PolicyActionClass.BUILD_TEST, build.actionClass)
        assertEquals(listOf("./gradlew", "test", "jar", "--no-daemon"), build.command)
        assertEquals(PolicyDecisionType.ALLOW, engine.evaluate(build.toRequest()).decision)

        val smoke = VerificationActionProposals.smoke(listOf("echo", "ok"), repoRoot, ActionActor.HumanOwner)
        assertEquals(PolicyActionClass.SMOKE, smoke.actionClass)
        assertEquals(PolicyDecisionType.ALLOW, engine.evaluate(smoke.toRequest()).decision)

        // The action classes are not interchangeable: the engine restricts
        // BUILD_TEST to a known launcher.
        val wrongClass = VerificationActionProposals.buildTest(listOf("echo", "ok"), repoRoot, ActionActor.HumanOwner)
        assertEquals(PolicyDecisionType.DENY, engine.evaluate(wrongClass.toRequest()).decision)
    }
}
