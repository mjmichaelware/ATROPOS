package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.security.RedactionFilter

/**
 * Builds the failed [AgentPatchAttempt] shapes.
 *
 * Four ways a patch request fails, each needing a different reason line and a
 * different amount of the response to be safe to keep. Collecting them here
 * means the redaction step cannot be forgotten on one path — which is the whole
 * risk, since every preview here is built from unverified provider output.
 *
 * Previews go through [RedactionFilter] without exception. A response that was
 * rejected is exactly the one most likely to contain something echoed back out
 * of the context it was given.
 */
internal class AgentPatchAttemptFactory(
    private val patchExtractor: AgentPatchExtractor,
    private val validator: AgentPatchResponseValidator,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val failureSummary: AgentFailureSummary = AgentFailureSummary(redactionFilter)
) {

    /** The provider answered, but the answer was not a usable diff. */
    fun patchFailure(result: ProviderCascadeResult, retryAttempted: Boolean): AgentPatchAttempt =
        AgentPatchAttempt(
            result = result,
            extraction = patchExtractor.extract(result.response)
                ?: AgentPatchResponseValidator.emptyExtraction(),
            retryAttempted = retryAttempted,
            rejectionReason = validator.rejectionReason(result.response),
            responsePreview = preview(result.response)
        )

    /** The answer could not be tied back to the context it was asked against. */
    fun attestationFailure(result: ProviderCascadeResult, retryAttempted: Boolean): AgentPatchAttempt =
        AgentPatchAttempt(
            result = result,
            extraction = AgentPatchResponseValidator.emptyExtraction(),
            retryAttempted = retryAttempted,
            rejectionReason = ATTESTATION_FAILED,
            responsePreview = preview(result.response)
        )

    /**
     * The call itself threw, so there is no response at all.
     *
     * The message is compacted rather than dropped: a transport failure is
     * usually the most informative thing an operator can be told, and it is also
     * the text most likely to contain a URL with credentials in it.
     */
    fun exceptionFailure(
        provider: String,
        failure: Exception,
        retryAttempted: Boolean
    ): AgentPatchAttempt {
        val message = compact(failure.message)
        return AgentPatchAttempt(
            result = ProviderCascadeResult(providerName = provider, response = "", errors = emptyList()),
            extraction = AgentPatchResponseValidator.emptyExtraction(),
            retryAttempted = retryAttempted,
            rejectionReason = message,
            responsePreview = message
        )
    }

    /**
     * The request was refused before any provider was contacted.
     *
     * Used for context-boundary refusals — a truncated or unbound source pack —
     * where there is no provider to name because none was asked.
     */
    fun refusal(reason: String): AgentPatchAttempt =
        AgentPatchAttempt(
            result = ProviderCascadeResult(providerName = "none", response = "", errors = emptyList()),
            extraction = AgentPatchResponseValidator.emptyExtraction(),
            retryAttempted = false,
            rejectionReason = reason,
            responsePreview = reason
        )

    fun compact(message: String?): String = failureSummary.compact(message)

    private fun preview(response: String): String =
        redactionFilter.redact(patchExtractor.preview(response))

    private companion object {
        const val ATTESTATION_FAILED = "context attestation failed"
    }
}
