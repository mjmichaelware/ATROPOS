package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import java.nio.file.Path

/**
 * Walks the patch provider order until one returns a usable diff.
 *
 * The loop is all that is left here. Judging a response, naming why it failed,
 * building the failure record, and checking attestation now belong to
 * [AgentPatchResponseValidator], [AgentPatchAttemptFactory], and
 * [AgentPatchAttestationGate] — the same three owners the repair path uses, so
 * the two cannot drift apart on what counts as a patch.
 *
 * ## One retry, then the next provider
 *
 * A provider that answers in prose gets exactly one corrective prompt naming
 * what was missing. Beyond that the cascade moves on: repeated reformulation
 * against a model that has already ignored the format spends quota without
 * changing the odds, and the next provider is the cheaper experiment.
 *
 * Attestation failure does not get a retry at all. It is not a formatting
 * problem — the response could not be tied to the context it was asked against,
 * and asking the same provider again produces another unverifiable answer.
 */
class AgentPatchCascadeRunner(
    private val router: ProviderCascadeRouter,
    private val patchExtractor: AgentPatchExtractor,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val repoRoot: Path,
    private val memoryStore: LocalMemoryStore,
    private val authorizeProvider: (String, String, String) -> Unit,
    private val completeWithCascade: (
        requestedProvider: String,
        prompt: String,
        context: String,
        providerOrderOverride: List<String>?,
        beforeAttempt: (String) -> Unit,
        contextEnvelope: ContextEnvelope?
    ) -> ProviderCascadeResult = { requestedProvider, prompt, context, providerOrderOverride, beforeAttempt, contextEnvelope ->
        router.completeWithCascade(
            requestedProvider = requestedProvider,
            prompt = prompt,
            context = context,
            providerOrderOverride = providerOrderOverride,
            beforeAttempt = beforeAttempt,
            contextEnvelope = contextEnvelope
        )
    }
) {
    private val validator = AgentPatchResponseValidator(patchExtractor)
    private val attempts = AgentPatchAttemptFactory(patchExtractor, validator, redactionFilter)
    private val attestation = AgentPatchAttestationGate()

    internal fun run(
        patchOrder: List<String>,
        prompt: String,
        context: String,
        truncated: Boolean = false
    ): AgentPatchCascadeResult {
        contextRefusal(context, truncated)?.let { reason ->
            return AgentPatchCascadeResult(failure = attempts.refusal(reason))
        }

        var lastFailure: AgentPatchAttempt? = null

        for (provider in patchOrder) {
            // try/catch rather than runCatching: `continue` cannot cross an
            // inline lambda boundary, and runCatching would also swallow Error,
            // which must keep propagating.
            val initial = try {
                runPatchAttempt(provider, prompt, context)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            accept(initial, retryAttempted = false)?.let { return AgentPatchCascadeResult(success = it) }
            if (!attested(initial)) {
                lastFailure = attempts.attestationFailure(initial, retryAttempted = false)
                continue
            }

            val retry = try {
                runPatchAttempt(provider, retryPrompt(prompt), context)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            accept(retry, retryAttempted = true)?.let { return AgentPatchCascadeResult(success = it) }
            if (!attested(retry)) {
                lastFailure = attempts.attestationFailure(retry, retryAttempted = true)
                continue
            }

            lastFailure = attempts.patchFailure(retry, retryAttempted = true)
        }

        return AgentPatchCascadeResult(failure = lastFailure)
    }

    /**
     * Why the request must not be sent at all, or null when it may proceed.
     *
     * Checked before the provider loop because a truncated or unbound source
     * pack is a property of the request, not of any one provider — trying the
     * cascade would send the same defective context to every provider in turn.
     */
    private fun contextRefusal(context: String, truncated: Boolean): String? {
        if (truncated) return "provider context refused: source context pack is truncated"
        return AgentProviderContextBoundary.validateSourcePack(
            context = context,
            sourcePackId = extractMarker(context, SOURCE_PACK_MARKER),
            fetchReceiptId = extractMarker(context, FETCH_RECEIPT_MARKER)
        )?.message
    }

    private fun accept(result: ProviderCascadeResult, retryAttempted: Boolean): AgentPatchAttempt? {
        if (!attestation.isAttested(result)) return null
        val extraction = validator.usableDiff(result.response) ?: return null
        return AgentPatchAttempt(result, extraction, retryAttempted)
    }

    /** Attestation for the failure path, which also records the refusal. */
    private fun attested(result: ProviderCascadeResult): Boolean =
        when (val verdict = attestation.evaluate(result)) {
            is AgentAttestationVerdict.Accepted -> true
            is AgentAttestationVerdict.Unattestable -> false
            is AgentAttestationVerdict.Refused -> {
                memoryStore.rememberDetailed(
                    kind = MemoryKind.SESSION,
                    title = "agent patch context attestation refused",
                    body = "${verdict.providerId}: ${verdict.reason}",
                    tags = listOf("agent", "patch", "context", "blocked"),
                    subjectType = "context_failure",
                    subjectId = null
                )
                false
            }
        }

    private fun retryPrompt(prompt: String): String = buildString {
        appendLine(prompt)
        appendLine()
        appendLine(
            "Your previous response was rejected because no unified diff was found. " +
                "Return ONLY a valid unified diff for the same task."
        )
        appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
    }.trimEnd()

    private fun runPatchAttempt(provider: String, prompt: String, context: String): ProviderCascadeResult {
        val envelope = ContextEnvelopeFactory.createSimple(provider, "", prompt, repoRoot)
        return completeWithCascade(
            provider,
            prompt,
            AgentPromptContract.buildPatch(
                context = context,
                providerId = provider,
                task = prompt,
                repoRoot = repoRoot
            ),
            listOf(provider),
            { candidate -> authorizeProvider(candidate, prompt, "patch") },
            envelope
        )
    }

    private fun extractMarker(context: String, prefix: String): String? =
        context.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val SOURCE_PACK_MARKER = "SOURCE_PACK_ID="
        const val FETCH_RECEIPT_MARKER = "FETCH_RECEIPT_ID="
    }
}
