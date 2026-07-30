package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelope
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import java.nio.file.Path

internal data class AgentPatchAttempt(
    val result: ProviderCascadeResult,
    val extraction: AgentPatchExtraction,
    val retryAttempted: Boolean,
    val rejectionReason: String? = null,
    val responsePreview: String? = null
)

internal data class AgentPatchCascadeResult(
    val success: AgentPatchAttempt? = null,
    val failure: AgentPatchAttempt? = null
)

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
    internal fun run(
        patchOrder: List<String>,
        prompt: String,
        context: String,
        truncated: Boolean = false
    ): AgentPatchCascadeResult {
        if (truncated) {
            return AgentPatchCascadeResult(
                failure = AgentPatchAttempt(
                    result = ProviderCascadeResult(providerName = "none", response = "", errors = emptyList()),
                    extraction = AgentPatchExtraction("", emptyList(), false),
                    retryAttempted = false,
                    rejectionReason = "provider context refused: source context pack is truncated",
                    responsePreview = "source context pack is truncated"
                )
            )
        }
        val contextRefusal = AgentProviderContextBoundary.validateSourcePack(
            context = context,
            sourcePackId = extractMarker(context, "SOURCE_PACK_ID="),
            fetchReceiptId = extractMarker(context, "FETCH_RECEIPT_ID=")
        )
        if (contextRefusal != null) {
            val reason = contextRefusal.message
            return AgentPatchCascadeResult(
                failure = AgentPatchAttempt(
                    result = ProviderCascadeResult(providerName = "none", response = "", errors = emptyList()),
                    extraction = AgentPatchExtraction("", emptyList(), false),
                    retryAttempted = false,
                    rejectionReason = reason,
                    responsePreview = reason
                )
            )
        }
        var lastFailure: AgentPatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, prompt, context)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            val accepted = validatePatchAttempt(initial, task = prompt)
            if (accepted != null) return AgentPatchCascadeResult(success = accepted)
            if (!isAttested(initial, prompt, recordFailure = true)) {
                lastFailure = buildAttestationFailure(initial, retryAttempted = false)
                continue
            }

            val retryPrompt = buildString {
                appendLine(prompt)
                appendLine()
                appendLine("Your previous response was rejected because no unified diff was found. Return ONLY a valid unified diff for the same task.")
                appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
            }
            val retry = try {
                runPatchAttempt(provider, retryPrompt.trimEnd(), context)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            val retryAccepted = validatePatchAttempt(retry, task = retryPrompt.trimEnd())
            if (retryAccepted != null) {
                return AgentPatchCascadeResult(success = retryAccepted.copy(retryAttempted = true))
            }
            if (!isAttested(retry, retryPrompt.trimEnd(), recordFailure = true)) {
                lastFailure = buildAttestationFailure(retry, retryAttempted = true)
                continue
            }

            lastFailure = buildPatchFailure(retry, retryAttempted = true)
        }

        return AgentPatchCascadeResult(failure = lastFailure)
    }

    private fun runPatchAttempt(provider: String, prompt: String, context: String): ProviderCascadeResult =
        ContextEnvelopeFactory.createSimple(provider, "", prompt, repoRoot).let { envelope ->
            completeWithCascade(
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

    private fun validatePatchAttempt(
        result: ProviderCascadeResult,
        retryAttempted: Boolean = false,
        task: String
    ): AgentPatchAttempt? {
        if (!isAttested(result, task, recordFailure = false)) return null
        val extraction = patchExtractor.extract(result.response) ?: return null
        if (!extraction.hasHunkBody) return null
        if (patchExtractor.validate(extraction.diff) != null) return null
        return AgentPatchAttempt(result, extraction, retryAttempted)
    }

    private fun isAttested(result: ProviderCascadeResult, task: String, recordFailure: Boolean): Boolean {
        val envelope = result.contextEnvelope ?: return false
        return when (val verified = ContextAttestationService.verify(envelope, result.response)) {
            is ContextAttestationService.VerifiedResult.Accepted -> true
            is ContextAttestationService.VerifiedResult.Rejected -> {
                if (recordFailure) {
                    memoryStore.rememberDetailed(
                        kind = MemoryKind.SESSION,
                        title = "agent patch context attestation refused",
                        body = "${verified.failure.providerId}: ${verified.failure.reason}",
                        tags = listOf("agent", "patch", "context", "blocked"),
                        subjectType = "context_failure",
                        subjectId = null
                    )
                }
                false
            }
        }
    }

    private fun buildPatchFailure(result: ProviderCascadeResult, retryAttempted: Boolean): AgentPatchAttempt {
        val extraction = patchExtractor.extract(result.response)
        val rejectionReason = when {
            extraction == null -> if (containsDiffHeader(result.response)) "diff body missing" else "no unified diff found"
            !extraction.hasHunkBody -> "diff body missing"
            else -> patchExtractor.validate(extraction.diff) ?: "unknown patch rejection"
        }

        return AgentPatchAttempt(
            result = result,
            extraction = extraction ?: AgentPatchExtraction("", emptyList(), false),
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = redactionFilter.redact(patchExtractor.preview(result.response))
        )
    }

    private fun buildAttestationFailure(result: ProviderCascadeResult, retryAttempted: Boolean): AgentPatchAttempt =
        AgentPatchAttempt(
            result = result,
            extraction = AgentPatchExtraction("", emptyList(), false),
            retryAttempted = retryAttempted,
            rejectionReason = "context attestation failed",
            responsePreview = redactionFilter.redact(patchExtractor.preview(result.response))
        )

    private fun buildExceptionFailure(provider: String, failure: Exception, retryAttempted: Boolean): AgentPatchAttempt {
        val message = failure.message?.trim()?.takeUnless { it.isBlank() }?.let { redactionFilter.compact(it, 240) }
            ?: "provider cascade failed"
        return AgentPatchAttempt(
            result = ProviderCascadeResult(providerName = provider, response = "", errors = emptyList()),
            extraction = AgentPatchExtraction("", emptyList(), false),
            retryAttempted = retryAttempted,
            rejectionReason = message,
            responsePreview = message
        )
    }

    private fun containsDiffHeader(text: String): Boolean =
        text.contains("diff --git ") || text.contains("\n--- ") || text.trimStart().startsWith("--- ")

    private fun extractMarker(context: String, prefix: String): String? =
        context.lineSequence()
            .firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}
