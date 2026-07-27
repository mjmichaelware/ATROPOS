/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ExecutionPolicyRequest
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import atropos.core.policy.TypedToolExecutor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 10 Batch 2 — a patch is provider-authored text. Nothing it describes may
 * reach a process, or the working tree, unless the system authorised it.
 */
class AgentPatchBoundedAgencyTest {

    private class SpawnCounter {
        val count = AtomicInteger(0)
        val seam: (List<String>, Path) -> Process = { _, _ ->
            count.incrementAndGet()
            error("bounded agency leaked: a refused patch proposal reached the process seam")
        }
    }

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

    private fun storeFor(
        repoRoot: Path,
        decision: PolicyDecisionType,
        reason: String,
        spawns: SpawnCounter
    ): AgentPatchStore {
        val gate = BoundedAgencyGate(FixedDecisionEngine(repoRoot, decision, reason))
        return AgentPatchStore(
            repoRoot = repoRoot,
            agencyGate = gate,
            agency = TypedToolExecutor(gate),
            spawn = spawns.seam
        )
    }

    private fun repo(): Path = Files.createTempDirectory("atropos-patch-agency-")

    private fun diffFile(repoRoot: Path): Path {
        val file = repoRoot.resolve("sample.diff")
        Files.writeString(file, "--- a/x\n+++ b/x\n@@ -1 +1 @@\n-a\n+b\n", StandardCharsets.UTF_8)
        return file
    }

    @Test
    fun blocked_apply_check_never_reaches_the_process_seam() {
        val repoRoot = repo()
        val spawns = SpawnCounter()
        val result = storeFor(repoRoot, PolicyDecisionType.DENY, "forbidden target path", spawns)
            .runGitApplyCheck(diffFile(repoRoot))

        assertEquals(0, spawns.count.get(), "a blocked patch check must not spawn git")
        assertEquals(AgencyDisposition.POLICY_BLOCKED, result.disposition)
        assertEquals(126, result.exitCode)
        assertTrue(!result.passed)
    }

    @Test
    fun blocked_apply_never_reaches_the_process_seam() {
        val repoRoot = repo()
        val spawns = SpawnCounter()
        val result = storeFor(repoRoot, PolicyDecisionType.DENY, "forbidden target path", spawns)
            .runGitApply(diffFile(repoRoot))

        assertEquals(0, spawns.count.get(), "a blocked mutation must not spawn git")
        assertEquals(AgencyDisposition.POLICY_BLOCKED, result.disposition)
        assertEquals(126, result.exitCode)
    }

    @Test
    fun approval_required_apply_is_withheld_and_distinct_from_blocked() {
        val repoRoot = repo()
        val blockedSpawns = SpawnCounter()
        val pendingSpawns = SpawnCounter()

        val blocked = storeFor(repoRoot, PolicyDecisionType.DENY, "denied", blockedSpawns)
            .runGitApply(diffFile(repoRoot))
        val pending = storeFor(repoRoot, PolicyDecisionType.APPROVAL_REQUIRED, "needs approval", pendingSpawns)
            .runGitApply(diffFile(repoRoot))

        assertEquals(0, blockedSpawns.count.get())
        assertEquals(0, pendingSpawns.count.get(), "an unapproved mutation must not spawn git")
        assertEquals(126, blocked.exitCode)
        assertEquals(125, pending.exitCode)
        assertTrue(blocked.disposition != pending.disposition)
        assertEquals(AgencyDisposition.APPROVAL_REQUIRED, pending.disposition)
    }

    @Test
    fun blocked_status_read_never_reaches_the_process_seam() {
        val repoRoot = repo()
        val spawns = SpawnCounter()
        val output = storeFor(repoRoot, PolicyDecisionType.DENY, "git mutation or remote command refused", spawns)
            .runGitStatusForPaths(listOf("src/main/kotlin/Example.kt"))

        assertEquals(0, spawns.count.get())
        assertEquals("git mutation or remote command refused", output)
    }

    @Test
    fun refusal_carries_a_proposal_id_a_compositor_can_act_on() {
        val repoRoot = repo()
        val result = storeFor(
            repoRoot,
            PolicyDecisionType.APPROVAL_REQUIRED,
            "needs approval",
            SpawnCounter()
        ).runGitApply(diffFile(repoRoot))

        assertNotNull(result.disposition)
        assertNotNull(result.proposalId)
        assertTrue(result.proposalId!!.startsWith("patch-"), result.proposalId!!)
    }

    @Test
    fun apply_patch_refuses_before_mutating_when_the_gate_withholds() {
        val repoRoot = repo()
        val spawns = SpawnCounter()
        val store = storeFor(repoRoot, PolicyDecisionType.APPROVAL_REQUIRED, "needs approval", spawns)

        val record = store.createRecord(
            provider = "test",
            task = "bounded agency",
            contextBytes = 0,
            diff = "--- a/notes.txt\n+++ b/notes.txt\n@@ -1 +1 @@\n-old\n+new\n"
        )
        val result = store.applyPatch(record.id, checkOnly = false)

        assertEquals(0, spawns.count.get(), "no git process may run for an unauthorised patch")
        assertTrue(!result.applied)
        assertEquals(AgencyDisposition.APPROVAL_REQUIRED, result.disposition)
        assertNotNull(result.proposalId)
        assertEquals("needs approval", result.refusalReason)
    }

    @Test
    fun real_policy_verdicts_are_unchanged_by_the_gate() {
        val repoRoot = repo()
        val spawns = SpawnCounter()
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
        val store = AgentPatchStore(
            repoRoot = repoRoot,
            agencyGate = gate,
            agency = TypedToolExecutor(gate),
            spawn = spawns.seam
        )

        // PATCH_APPLY with a forbidden target path is still denied, by the same
        // engine rules, through the new chokepoint.
        val forbidden = store.runGitStatusForPaths(listOf("build/output.jar"))
        assertEquals(0, spawns.count.get())
        assertEquals("forbidden target path", forbidden)
    }

    @Test
    fun proposals_reproduce_the_previous_policy_request() {
        val repoRoot = repo()
        val file = diffFile(repoRoot)
        val engine = ExecutionPolicyEngine(repoRoot)

        // The engine denies PATCH_APPLY without target paths, so a proposal that
        // dropped targetPaths would silently flip a verdict.
        val proposal = atropos.core.policy.PatchActionProposals.applyCheck(file, repoRoot)
        assertEquals(PolicyActionClass.PATCH_APPLY, proposal.actionClass)
        assertEquals(listOf("sample.diff"), proposal.targetPaths)
        assertEquals(listOf("git", "apply", "--check", file.toString()), proposal.command)
        assertEquals(PolicyDecisionType.ALLOW, engine.evaluate(proposal.toRequest()).decision)
    }
}
