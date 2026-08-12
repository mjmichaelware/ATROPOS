package atropos.core.integration

import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InboundActionProposalBridgeTest {
    private fun allowed(proposal: ActionProposal) = AgencyDecision(
        proposal = proposal,
        policyDecision = ExecutionPolicyDecision(
            id = "test", decision = PolicyDecisionType.ALLOW,
            actionClass = proposal.actionClass, destructive = false, reason = "test"
        ),
        disposition = AgencyDisposition.ALLOWED,
        reason = "test"
    )

    @Test
    fun mcp_is_converted_to_a_proposal_before_the_gate() {
        var seen: ActionProposal? = null
        val bridge = McpTerritoryBridge(setOf("inspect")) { proposal ->
            seen = proposal
            allowed(proposal)
        }

        val result = bridge.judge(InboundToolRequest(InboundSource.MCP, "m1", "inspect", listOf("src")))
        val judged = assertIs<InboundGateResult.Judged>(result)

        assertEquals(judged.proposal, seen)
        assertEquals(PolicyActionClass.FILE_MUTATION, judged.proposal.actionClass)
        assertEquals(AgencyDisposition.ALLOWED, judged.decision.disposition)
    }

    @Test
    fun computer_use_requires_surface_and_grant_before_gate() {
        var invoked = false
        val bridge = ComputerUseTerritoryBridge(setOf("click")) {
            invoked = true
            error("invalid computer-use request reached the gate")
        }
        val result = bridge.judge(
            InboundToolRequest(InboundSource.COMPUTER_USE, "c1", "click", listOf("src"))
        )

        assertIs<InboundGateResult.Refused>(result)
        assertTrue((result as InboundGateResult.Refused).reason.contains("target surface"))
        assertEquals(false, invoked)
    }

    @Test
    fun computer_use_carries_surface_and_grant_into_the_gated_proposal() {
        val bridge = ComputerUseTerritoryBridge(setOf("click")) { proposal -> allowed(proposal) }
        val result = bridge.judge(
            InboundToolRequest(
                InboundSource.COMPUTER_USE, "c1", "click", listOf("src"),
                targetSurface = "browser:preview", territoryGrantId = "grant-1"
            )
        )
        val judged = assertIs<InboundGateResult.Judged>(result)

        assertEquals("browser:preview", judged.proposal.metadata["targetSurface"])
        assertEquals("grant-1", judged.proposal.metadata["territoryGrantId"])
    }
}
