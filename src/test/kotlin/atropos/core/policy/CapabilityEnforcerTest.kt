package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityEnforcerTest {
    @Test
    fun capability_owner_allows_a_proposal_without_requirements() {
        val proposal = ActionProposal(
            id = "capability-none",
            actionClass = PolicyActionClass.NETWORK,
            actor = ActionActor.HumanOwner,
            command = listOf("status"),
            metadata = emptyMap()
        )
        assertEquals(null, CapabilityEnforcer().evaluate(proposal))
    }

    @Test
    fun boundedAgencyGateBlocksMissingCapabilitiesBeforeExecutionPolicy() {
        val repoRoot = Files.createTempDirectory("atropos-capability-block-")
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))

        val decision = gate.evaluate(
            ActionProposal(
                id = "capability-block",
                actionClass = PolicyActionClass.SHELL,
                actor = ActionActor.HierarchyNode("worker", "node-1"),
                command = listOf("pwd"),
                metadata = mapOf(
                    "requiredCapabilities" to "shell,verify",
                    "grantedCapabilities" to "verify"
                )
            )
        )

        assertEquals(AgencyDisposition.POLICY_BLOCKED, decision.disposition)
        assertEquals("capability", decision.policyDecision.id)
        assertTrue(decision.reason.contains("shell"), decision.reason)
    }

    @Test
    fun grantedCapabilitiesContinueIntoExistingPolicyEngine() {
        val repoRoot = Files.createTempDirectory("atropos-capability-allow-")
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))

        val decision = gate.evaluate(
            ActionProposal(
                id = "capability-allow",
                actionClass = PolicyActionClass.SHELL,
                actor = ActionActor.HumanOwner,
                command = listOf("pwd"),
                metadata = mapOf(
                    "requiredCapabilities" to "shell",
                    "grantedCapabilities" to "shell,verify"
                )
            )
        )

        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }
}
