/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 10 Batch 5 — lifecycle control travels the same road as everything
 * else, rather than being the one authority that answers to itself.
 */
class LifecycleActionProposalsTest {

    private fun gate() = BoundedAgencyGate(
        ExecutionPolicyEngine(Files.createTempDirectory("atropos-lifecycle-agency-"))
    )

    @Test
    fun daemon_proposal_reproduces_the_previous_policy_request() {
        val proposal = LifecycleActionProposals.daemon("start")

        assertEquals(PolicyActionClass.DAEMON, proposal.actionClass)
        assertEquals(mapOf("operation" to "start"), proposal.metadata)
        assertTrue(proposal.command.isEmpty(), "a lifecycle transition carries no command")
        assertTrue(proposal.targetPaths.isEmpty(), "a lifecycle transition touches no paths")
    }

    @Test
    fun queue_proposal_reproduces_the_previous_policy_request() {
        val proposal = LifecycleActionProposals.queue("lease", "job-7")

        assertEquals(PolicyActionClass.QUEUE, proposal.actionClass)
        assertEquals(mapOf("operation" to "lease", "detail" to "job-7"), proposal.metadata)
        assertTrue(proposal.command.isEmpty())
    }

    @Test
    fun queue_detail_defaults_to_blank_as_it_did_before() {
        assertEquals(
            mapOf("operation" to "enqueue", "detail" to ""),
            LifecycleActionProposals.queue("enqueue").metadata
        )
    }

    @Test
    fun lifecycle_transitions_are_allowed_through_the_gate() {
        val gate = gate()
        assertEquals(
            AgencyDisposition.ALLOWED,
            gate.evaluate(LifecycleActionProposals.daemon("stop")).disposition
        )
        assertEquals(
            AgencyDisposition.ALLOWED,
            gate.evaluate(LifecycleActionProposals.queue("complete", "job-1")).disposition
        )
    }

    @Test
    fun the_gate_delegates_rather_than_deciding() {
        // Same proposal, engine and gate must reach the same verdict — the gate
        // maps a decision, it does not form one.
        val repoRoot = Files.createTempDirectory("atropos-lifecycle-parity-")
        val engine = ExecutionPolicyEngine(repoRoot)
        val proposal = LifecycleActionProposals.queue("lease", "job-9")

        val direct = engine.evaluate(proposal.toRequest())
        val viaGate = BoundedAgencyGate(engine).evaluate(proposal)

        assertEquals(direct.decision, viaGate.policyDecision.decision)
        assertEquals(AgencyDisposition.ALLOWED, viaGate.disposition)
    }
}
