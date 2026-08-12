package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.memory.LocalMemoryStore

/**
 * Validates repair responses and checks attestation.
 *
 * Ensures responses meet validation criteria and pass attestation checks
 * before being accepted as repairs.
 */
internal class AgentRepairValidator(
    private val validator: AgentPatchResponseValidator,
    private val attestation: AgentPatchAttestationGate,
    private val memoryStore: LocalMemoryStore
) {
    /**
     * Turns a repair response into an accepted attempt, or null.
     *
     * Attestation runs first and its refusal is recorded. Repair was the one
     * live path where model output became a repository mutation without its
     * response ever being checked against its envelope; refusing here means the
     * patch is never stored rather than being rejected further downstream.
     */
    fun accept(
        result: ProviderCascadeResult,
        retryAttempted: Boolean = false
    ): AgentPatchAttempt? {
        if (!attested(result)) return null
        val extraction = validator.usableDiff(result.response) ?: return null
        return AgentPatchAttempt(result, extraction, retryAttempted)
    }

    private fun attested(result: ProviderCascadeResult): Boolean =
        when (val verdict = attestation.evaluate(result)) {
            is AgentAttestationVerdict.Accepted -> true
            is AgentAttestationVerdict.Unattestable -> false
            is AgentAttestationVerdict.Refused -> {
                memoryStore.rememberFailure(
                    subjectType = "context_failure",
                    subjectId = null,
                    title = verdict.failureName,
                    body = "repair ${verdict.providerId}: ${verdict.reason}",
                    tags = listOf("context", "attestation", "failure", "repair")
                )
                false
            }
        }
}
