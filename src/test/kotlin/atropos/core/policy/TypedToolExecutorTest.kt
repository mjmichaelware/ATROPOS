package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedToolExecutorTest {
    @Test
    fun bounded_agency_gate_blocks_paid_provider_proposals() {
        val repoRoot = Files.createTempDirectory("atropos-policy-gate-")
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))

        val decision = gate.evaluate(
            ActionProposal(
                id = "proposal-paid",
                actionClass = PolicyActionClass.PROVIDER_CALL,
                actor = ActionActor.HumanOwner,
                providerId = "openai",
                paidProvider = true
            )
        )

        assertEquals(AgencyDisposition.POLICY_BLOCKED, decision.disposition)
        assertEquals(PolicyDecisionType.DENY, decision.policyDecision.decision)
    }

    @Test
    fun typed_tool_executor_requires_approval_for_network_actions() {
        val repoRoot = Files.createTempDirectory("atropos-policy-network-")
        val executor = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)))

        val result = executor.execute(
            ActionProposal(
                id = "proposal-network",
                actionClass = PolicyActionClass.NETWORK,
                actor = ActionActor.HumanOwner,
                networkTarget = "https://example.invalid"
            )
        )

        assertFalse(result.authorized)
        assertFalse(result.executed)
        assertEquals(AgencyDisposition.APPROVAL_REQUIRED, result.disposition)
        assertEquals(PolicyDecisionType.APPROVAL_REQUIRED, result.policyDecision.decision)
    }

    @Test
    fun typed_tool_executor_runs_bound_executor_only_after_policy_allow() {
        val repoRoot = Files.createTempDirectory("atropos-policy-shell-")
        val executor = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)))

        val result = executor.execute(
            ActionProposal(
                id = "proposal-shell",
                actionClass = PolicyActionClass.SHELL,
                actor = ActionActor.HumanOwner,
                command = listOf("pwd")
            )
        ) { "ok" }

        assertTrue(result.authorized)
        assertTrue(result.executed)
        assertEquals("ok", result.output)
        assertEquals(AgencyDisposition.ALLOWED, result.disposition)
    }
}
