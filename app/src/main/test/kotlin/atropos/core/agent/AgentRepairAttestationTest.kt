/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.ProviderCascadeResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch 14 — repair was the one live path where model output became a
 * repository mutation without its response ever being checked against the
 * context it was given.
 *
 * `validatePatchAttempt` is private, so these pin the contract it now enforces
 * at the layer it delegates to: an unattested response is rejected, an attested
 * one is accepted and its patch text survives verification intact.
 */
class AgentRepairAttestationTest {

    private fun envelope(task: String) = ContextEnvelopeFactory.createSimple(
        providerId = "groq",
        modelId = "",
        task = task,
        repoRoot = Files.createTempDirectory("atropos-repair-attest-")
    )

    private val diff = """
        --- a/notes.txt
        +++ b/notes.txt
        @@ -1 +1 @@
        -old
        +new
    """.trimIndent()

    @Test
    fun a_repair_response_with_no_attestation_block_is_rejected() {
        val verified = ContextAttestationService.verify(envelope("repair the failure"), diff)

        assertTrue(
            verified is ContextAttestationService.VerifiedResult.Rejected,
            "an unattested repair diff must not become a patch"
        )
    }

    @Test
    fun a_repair_response_attesting_to_a_different_context_is_rejected() {
        val other = envelope("some entirely different task")
        val response = diff + "\n" + attestationBlockFor(other)

        val verified = ContextAttestationService.verify(envelope("repair the failure"), response)

        assertTrue(
            verified is ContextAttestationService.VerifiedResult.Rejected,
            "a response attesting to another envelope must not pass"
        )
    }

    @Test
    fun an_attested_repair_response_is_accepted_and_keeps_its_diff() {
        val env = envelope("repair the failure")
        val response = diff + "\n" + attestationBlockFor(env)

        val verified = ContextAttestationService.verify(env, response)

        assertTrue(verified is ContextAttestationService.VerifiedResult.Accepted, "attested repair must pass")
        val cleaned = (verified as ContextAttestationService.VerifiedResult.Accepted).cleanedResponse
        assertTrue(cleaned.contains("+new"), "the patch body must survive verification: $cleaned")
        assertTrue(
            !cleaned.contains("ATROPOS CONTEXT ATTESTATION"),
            "the attestation block must not leak into the stored diff"
        )
    }

    @Test
    fun a_result_without_the_dispatched_envelope_cannot_be_attested_as_repair() {
        val env = envelope("repair the failure")
        val response = diff + "\n" + attestationBlockFor(env)
        val result = ProviderCascadeResult("groq", response, emptyList())

        assertNull(result.contextEnvelope, "a provider result without dispatch context must fail closed")
        val attestedResult = result.copy(contextEnvelope = env)
        assertTrue(
            ContextAttestationService.verify(
                attestedResult.contextEnvelope!!,
                attestedResult.response
            ) is ContextAttestationService.VerifiedResult.Accepted,
            "only the envelope captured at dispatch may authorize repair output"
        )
    }

    private fun attestationBlockFor(env: atropos.core.provider.ContextEnvelope): String = buildString {
        appendLine("--- ATROPOS CONTEXT ATTESTATION ---")
        appendLine("systemIdentity=${env.systemIdentity}")
        appendLine("repository=${env.repository}")
        appendLine("taskOrNodeId=${env.task}")
        appendLine("role=${env.hierarchyRole}")
        appendLine("contextVersion=${env.contextVersion}")
        appendLine("contextHash=${env.canonicalContextHash}")
        appendLine("--- END ATROPOS CONTEXT ATTESTATION ---")
    }
}
