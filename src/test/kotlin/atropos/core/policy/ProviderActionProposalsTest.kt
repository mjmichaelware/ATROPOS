/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                ProviderActionProposals.forCall(provider, "patch", 128)
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
            ProviderActionProposals.forCall("groq", "patch", 128)
        )
        assertEquals(AgencyDisposition.ALLOWED, decision.disposition)
    }

    @Test
    fun a_blank_provider_is_still_refused() {
        val decision = gate().evaluate(
            ProviderActionProposals.forCall("", "patch", 1)
        )
        assertEquals(AgencyDisposition.POLICY_BLOCKED, decision.disposition)
    }

    @Test
    fun proposal_reproduces_the_previous_policy_request() {
        val proposal = ProviderActionProposals.forCall("groq", "repair", 4_096)

        assertEquals(PolicyActionClass.PROVIDER_CALL, proposal.actionClass)
        assertEquals("groq", proposal.providerId)
        assertFalse(proposal.paidProvider)
        assertEquals(
            mapOf("operation" to "repair", "prompt_length" to "4096"),
            proposal.metadata
        )

        // The prompt itself must never travel into policy metadata.
        assertTrue(proposal.metadata.values.none { it.length > 32 })
    }

    @Test
    fun paid_flag_tracks_the_single_canonical_set() {
        assertTrue(ProviderActionProposals.isPaid("openai"))
        assertTrue(ProviderActionProposals.forCall("anthropic", "patch", 1).paidProvider)
        assertFalse(ProviderActionProposals.isPaid("groq"))
        assertFalse(ProviderActionProposals.forCall("groq", "patch", 1).paidProvider)
    }
}
