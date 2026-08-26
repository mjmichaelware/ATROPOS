/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import atropos.core.paid.EmergencyPaidGate

/**
 * Phase 10 Batch 3 — the paid-provider lock now has one definition, and it must
 * still refuse through the gate exactly as it did through the engine.
 */
class ProviderActionProposalsTest {

    private fun gate() = BoundedAgencyGate(
        ExecutionPolicyEngine(Files.createTempDirectory("atropos-provider-agency-"))
    )

    @Test
    fun every_paid_provider_is_blocked_through_the_gate() {
        val gate = gate()
        ProviderActionProposals.PAID_PROVIDERS.forEach { provider ->
            val decision = gate.evaluate(
                ProviderActionProposals.forCall(provider, "patch", 128, ActionActor.HumanOwner)
            )
            assertEquals(
                AgencyDisposition.POLICY_BLOCKED,
                decision.disposition,
                "paid provider '$provider' must stay locked"
            )
            assertEquals(PolicyDecisionType.DENY, decision.policyDecision.decision)
        }
    }

    @Test
    fun free_providers_are_allowed_through_the_gate() {
        val decision = gate().evaluate(
            ProviderActionProposals.forCall("groq", "patch", 128, ActionActor.HumanOwner)
        )
        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }

    @Test
    fun a_blank_provider_is_still_refused() {
        val decision = gate().evaluate(
            ProviderActionProposals.forCall("", "patch", 1, ActionActor.HumanOwner)
        )
        assertEquals(AgencyDisposition.POLICY_BLOCKED, decision.disposition)
    }

    @Test
    fun proposal_reproduces_the_previous_policy_request() {
        val proposal = ProviderActionProposals.forCall("groq", "repair", 4_096, ActionActor.HumanOwner)

        assertEquals(PolicyActionClass.PROVIDER_CALL, proposal.actionClass)
        assertEquals("groq", proposal.providerId)
        assertFalse(proposal.paidProvider)
        assertEquals(
            mapOf("operation" to "repair", "prompt_length" to "4096", "provider_local" to "false"),
            proposal.metadata
        )

        // The prompt itself must never travel into policy metadata.
        assertTrue(proposal.metadata.values.none { it.length > 32 })
    }

    @Test
    fun paid_flag_tracks_the_single_canonical_set() {
        assertTrue(ProviderActionProposals.isPaid("openai"))
        assertTrue(ProviderActionProposals.isPaid("cerebras"))
        assertTrue(ProviderActionProposals.forCall("deepinfra", "chat", 1, ActionActor.HumanOwner).paidProvider)
        assertTrue(ProviderActionProposals.forCall("anthropic", "patch", 1, ActionActor.HumanOwner).paidProvider)
        assertFalse(ProviderActionProposals.isPaid("groq"))
        assertFalse(ProviderActionProposals.forCall("groq", "patch", 1, ActionActor.HumanOwner).paidProvider)
    }

    @Test
    fun local_only_engine_blocks_remote_provider_and_network_but_allows_local_provider() {
        val engine = ExecutionPolicyEngine(
            repoRoot = Files.createTempDirectory("atropos-local-only"),
            localOnly = true
        )
        val remote = engine.evaluate(ProviderActionProposals.forCall("groq", "chat", 8, ActionActor.HumanOwner).toRequest())
        assertEquals(PolicyDecisionType.DENY, remote.decision)
        assertTrue(remote.reason.contains("local-only"))

        val local = engine.evaluate(ProviderActionProposals.forCall("ollama", "chat", 8, ActionActor.HumanOwner).toRequest())
        assertEquals(PolicyDecisionType.ALLOW, local.decision)

        val network = engine.evaluate(
            ExecutionPolicyRequest(
                actionClass = PolicyActionClass.NETWORK,
                networkTarget = "https://example.invalid"
            )
        )
        assertEquals(PolicyDecisionType.DENY, network.decision)
    }

    @Test
    fun explicitly_unlocked_paid_provider_can_pass_the_same_policy_engine() {
        val root = Files.createTempDirectory("atropos-paid-approved")
        val paidGate = EmergencyPaidGate(root.resolve("paid").toFile())
        paidGate.unlock("openai", "1m", "operator approved")
        val engine = ExecutionPolicyEngine(root, paidGate = paidGate)
        val decision = engine.evaluate(
            ProviderActionProposals.forCall("openai", "chat", 8, ActionActor.HumanOwner).toRequest()
        )
        assertEquals(PolicyDecisionType.ALLOW, decision.decision)
    }
}
