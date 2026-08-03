package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelope

/**
 * One corrective retry after an ask fails context attestation.
 *
 * ## What the corrective preamble is actually for
 *
 * The dominant attestation failure is not a model refusing to comply — it is a
 * model answering about the wrong ATROPOS. The name belongs to one of the Fates
 * in Greek mythology, and a model given a repository context pack and a
 * question about "ATROPOS" will sometimes answer mythologically, producing a
 * fluent response that has nothing to do with the codebase and carries no
 * attestation block.
 *
 * The preamble says three things: the previous answer was rejected, ATROPOS is
 * this repository and runtime rather than the myth, and the attestation block
 * is required. That is usually enough.
 *
 * ## Exactly one retry, and it must attest
 *
 * The retry is verified against the same envelope, and an unattested retry
 * returns null rather than being handed back as a better-than-nothing answer.
 * An unverified response is precisely what the first attempt already produced;
 * accepting it on the second try would make attestation advisory.
 *
 * Failures throw nothing. A retry is already the fallback path, so an exception
 * here means falling through to the local answer rather than replacing one
 * failure with another.
 */
internal class AgentAskAttestationRetry(
    private val router: ProviderCascadeRouter,
    private val authorizeProvider: (provider: String, prompt: String, operation: String) -> Unit,
    private val verify: (ContextEnvelope, String) -> ContextAttestationService.VerifiedResult =
        ContextAttestationService::verify
) {

    /** @return the retry result when it attested, null when it did not or could not run. */
    fun retry(
        providerId: String,
        sanitizedTask: String,
        context: String,
        envelope: ContextEnvelope
    ): ProviderCascadeResult? = try {
        val result = router.completeWithCascade(
            requestedProvider = providerId,
            prompt = sanitizedTask,
            context = AgentPromptContract.buildWithEnvelope(
                context = CORRECTIVE_PREAMBLE + context,
                envelope = envelope
            ),
            beforeAttempt = { provider -> authorizeProvider(provider, sanitizedTask, ASK_OPERATION) },
            contextEnvelope = envelope
        )
        when (verify(envelope, result.response)) {
            is ContextAttestationService.VerifiedResult.Accepted -> result
            is ContextAttestationService.VerifiedResult.Rejected -> null
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ASK_OPERATION = "ask"

        const val CORRECTIVE_PREAMBLE =
            "ATROPOS: retrying after context attestation failure. " +
                "The previous response was rejected. You are operating inside the ATROPOS software " +
                "engine. ATROPOS refers to this repository and runtime, not Greek mythology. " +
                "Include the required attestation block in your response.\n\n"
    }
}
