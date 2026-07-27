/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.security.RedactionFilter

/**
 * Orchestrates provider context envelope injection and response attestation
 * verification for every provider call path.
 *
 * Flow:
 * 1. Build [ContextEnvelope] for the current call
 * 2. Inject envelope into the prompt via [ProviderContextInjector]
 * 3. Receive provider response
 * 4. Parse response via [ProviderResponseContextParser]
 * 5. Verify attestation via [ContextDriftDetector]
 * 6. Return verified result or typed failure
 */
object ContextAttestationService {

    private val redactionFilter = RedactionFilter()

    /**
     * Result of a verified provider call.
     */
    sealed class VerifiedResult {
        /** The provider response passed all attestation checks and is safe to use. */
        data class Accepted(
            val cleanedResponse: String,
            val envelope: ContextEnvelope,
            val attestation: ContextAttestation
        ) : VerifiedResult()

        /** The provider response failed one or more attestation checks. */
        data class Rejected(
            val failure: TypedContextFailure,
            val envelope: ContextEnvelope,
            val providerResponse: String
        ) : VerifiedResult()
    }

    /**
     * Verify a provider response against the sent envelope.
     *
     * @param envelope the envelope that was sent to the provider
     * @param providerResponse the raw response text from the provider
     * @return [VerifiedResult.Accepted] if attestation is valid, or
     *         [VerifiedResult.Rejected] with a [TypedContextFailure]
     */
    fun verify(
        envelope: ContextEnvelope,
        providerResponse: String
    ): VerifiedResult {
        // Parse the response
        val parsed = ProviderResponseContextParser.parse(providerResponse, envelope)

        // Check for unsolicited mythology
        if (parsed.mythologyDetected) {
            return VerifiedResult.Rejected(
                failure = TypedContextFailure.MythologyAnswer(
                    providerId = envelope.providerId,
                    reason = "provider returned Greek mythology content without explicit mythology request"
                ),
                envelope = envelope,
                providerResponse = redactionFilter.redact(parsed.cleanedResponse)
            )
        }

        // Check attestation presence
        val attestation = parsed.attestation
        if (attestation == null) {
            return VerifiedResult.Rejected(
                failure = TypedContextFailure.MissingAttestation(
                    providerId = envelope.providerId,
                    reason = "provider response does not include a context attestation block"
                ),
                envelope = envelope,
                providerResponse = redactionFilter.redact(parsed.cleanedResponse)
            )
        }

        // Check attestation completeness
        if (attestation.systemIdentity.isBlank() || attestation.repository.isBlank() ||
            attestation.contextVersion.isBlank() || attestation.contextHash.isBlank()
        ) {
            return VerifiedResult.Rejected(
                failure = TypedContextFailure.MalformedAttestation(
                    providerId = envelope.providerId,
                    reason = "attestation block is missing required fields"
                ),
                envelope = envelope,
                providerResponse = redactionFilter.redact(parsed.cleanedResponse)
            )
        }

        // Detect drift between envelope and attestation
        val drift = ContextDriftDetector.detect(envelope, attestation)
        if (drift != null) {
            return VerifiedResult.Rejected(
                failure = drift,
                envelope = envelope,
                providerResponse = redactionFilter.redact(parsed.cleanedResponse)
            )
        }

        // All checks passed
        return VerifiedResult.Accepted(
            cleanedResponse = parsed.cleanedResponse,
            envelope = envelope,
            attestation = attestation
        )
    }

    /**
     * Inject context into a prompt and return the full prompt text
     * complete with envelope, identity instruction, and attestation
     * requirement.
     */
    fun injectContext(
        envelope: ContextEnvelope,
        originalPrompt: String,
        explicitMythologyRequest: Boolean = false
    ): String =
        ProviderContextInjector.inject(envelope, originalPrompt, explicitMythologyRequest)
}
