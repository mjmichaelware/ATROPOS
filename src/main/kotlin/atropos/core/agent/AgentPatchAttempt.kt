package atropos.core.agent

import atropos.core.ProviderCascadeResult

/**
 * One provider's answer to a patch request, and what became of it.
 *
 * Lifted out of [AgentPatchCascadeRunner] so that the repair path can share the
 * shape instead of declaring its own. Both paths ask a provider for a unified
 * diff, judge the response the same way, and report the same failure vocabulary;
 * they had two private copies of this type, which meant a change to one path's
 * failure reporting silently did not reach the other.
 *
 * @param rejectionReason null when the attempt was accepted. Non-null values are
 *   operator-facing text explaining why a response was not usable as a patch.
 * @param responsePreview a redacted excerpt kept for diagnosis. Never the raw
 *   response — a rejected provider answer can still contain whatever the model
 *   echoed back from its context.
 */
internal data class AgentPatchAttempt(
    val result: ProviderCascadeResult,
    val extraction: AgentPatchExtraction,
    val retryAttempted: Boolean,
    val rejectionReason: String? = null,
    val responsePreview: String? = null
) {
    /**
     * Marks an accepted attempt as having taken a retry.
     *
     * Named rather than using the generated `copy` because the cascade only ever
     * rewrites this one field, and a bare `copy(...)` at the call site reads as
     * though the whole attempt were being rebuilt.
     */
    fun withRetryAttempted(value: Boolean): AgentPatchAttempt = copy(retryAttempted = value)
}

/**
 * The outcome of walking the provider order.
 *
 * Exactly one side is populated: [success] when some provider produced a usable
 * diff, [failure] with the last failure otherwise. Both null means the provider
 * order was empty — there was nothing to try, which is distinct from having
 * tried and been refused.
 */
internal data class AgentPatchCascadeResult(
    val success: AgentPatchAttempt? = null,
    val failure: AgentPatchAttempt? = null
)
