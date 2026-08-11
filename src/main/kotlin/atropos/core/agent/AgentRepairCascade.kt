package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ProviderActionProposals
import atropos.core.provider.ContextEnvelopeFactory

/**
 * Manages the cascade loop and attempt execution for repairs.
 *
 * Handles provider cascade iteration, retry logic, and attempt execution.
 */
internal class AgentRepairCascade(
    private val router: ProviderCascadeRouter,
    private val collector: AgentContextCollector,
    private val validator: AgentRepairValidator,
    private val attempts: AgentPatchAttemptFactory,
    private val agencyGate: BoundedAgencyGate
) {
    fun runCascade(
        patchOrder: List<String>,
        repairContext: AgentRepairPromptContext
    ): AgentPatchCascadeResult {
        var lastFailure: AgentPatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, AgentRepairPromptBuilder.REPAIR_PROMPT, repairContext)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            validator.accept(initial, retryAttempted = false)
                ?.let { return AgentPatchCascadeResult(success = it) }

            val retry = try {
                runPatchAttempt(provider, AgentRepairPromptBuilder().buildRetryPrompt(), repairContext)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            validator.accept(retry, retryAttempted = true)
                ?.let { return AgentPatchCascadeResult(success = it) }

            lastFailure = attempts.patchFailure(retry, retryAttempted = true)
        }

        return AgentPatchCascadeResult(failure = lastFailure)
    }

    private fun runPatchAttempt(
        provider: String,
        prompt: String,
        repairContext: AgentRepairPromptContext
    ): ProviderCascadeResult {
        val envelope = ContextEnvelopeFactory.createSimple(
            providerId = provider,
            modelId = "",
            task = repairContext.attestationTask,
            repoRoot = collector.repoRoot
        )
        return router.completeWithCascade(
            requestedProvider = provider,
            prompt = prompt,
            context = AgentPromptContract.buildRepairWithEnvelope(
                patchId = repairContext.patchId,
                changedPaths = repairContext.changedPaths,
                failedCommand = repairContext.failedCommand,
                exitCode = repairContext.exitCode,
                durationMillis = repairContext.durationMillis,
                stdout = repairContext.stdout,
                stderr = repairContext.stderr,
                context = repairContext.context,
                envelope = envelope
            ),
            providerOrderOverride = listOf(provider),
            beforeAttempt = { candidate ->
                enforceProviderPolicy(candidate, prompt, repairContext.patchId)
            },
            contextEnvelope = envelope
        )
    }

    private fun enforceProviderPolicy(provider: String, prompt: String, patchId: String) {
        val decision = agencyGate.evaluate(
            ProviderActionProposals.forCall(
                provider,
                "repair",
                prompt.length,
                ActionActor.HierarchyNode(role = "repair", nodeId = patchId)
            )
        )
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }
}
