package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.provider.ContextAttestationService

/**
 * Checks that a provider answered against the context it was given.
 *
 * Both the patch and repair paths ran this check with their own private copy of
 * the logic and their own memory-record shape. The check is now one owner; the
 * recording stays with the callers, because what they write differs
 * legitimately — a refused repair and a refused patch are different events in
 * the durable record and should not be flattened into one.
 *
 * ## A missing envelope is not an accepted attestation
 *
 * [AgentAttestationVerdict.Unattestable] exists so callers cannot treat "there
 * was nothing to check" as "the check passed". Returning a bare boolean made
 * those two indistinguishable, and the safe reading of an unverifiable response
 * is that it is unverified.
 */
internal class AgentPatchAttestationGate(
    private val verify: (envelope: atropos.core.provider.ContextEnvelope, response: String) ->
    ContextAttestationService.VerifiedResult = ContextAttestationService::verify
) {

    fun evaluate(result: ProviderCascadeResult): AgentAttestationVerdict {
        val envelope = result.contextEnvelope ?: return AgentAttestationVerdict.Unattestable
        return when (val verified = verify(envelope, result.response)) {
            is ContextAttestationService.VerifiedResult.Accepted -> AgentAttestationVerdict.Accepted
            is ContextAttestationService.VerifiedResult.Rejected -> AgentAttestationVerdict.Refused(
                providerId = verified.failure.providerId,
                reason = verified.failure.reason,
                failureName = verified.failure.javaClass.simpleName
            )
        }
    }

    /** Convenience for call sites that only branch on pass/fail. */
    fun isAttested(result: ProviderCascadeResult): Boolean =
        evaluate(result) is AgentAttestationVerdict.Accepted
}

/** Whether a response can be trusted to have been produced against its envelope. */
internal sealed interface AgentAttestationVerdict {

    /** Verified against the envelope the call was made under. */
    data object Accepted : AgentAttestationVerdict

    /** No envelope was attached, so nothing could be verified. Never a pass. */
    data object Unattestable : AgentAttestationVerdict

    /** Verified and found not to match. */
    data class Refused(
        val providerId: String,
        val reason: String,
        val failureName: String
    ) : AgentAttestationVerdict
}
