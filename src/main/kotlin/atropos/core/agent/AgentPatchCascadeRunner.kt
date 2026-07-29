package atropos.core.agent

import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.security.RedactionFilter

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
    private val authorizeProvider: (String, String, String) -> Unit
) {
    internal fun run(patchOrder: List<String>, prompt: String, context: String): AgentPatchCascadeResult {
        val body = AgentPromptContract.buildPatch(context)
        var lastFailure: AgentPatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, prompt, body)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            val accepted = validatePatchAttempt(initial)
            if (accepted != null) return AgentPatchCascadeResult(success = accepted)

            val retryPrompt = buildString {
                appendLine(prompt)
                appendLine()
                appendLine("Your previous response was rejected because no unified diff was found. Return ONLY a valid unified diff for the same task.")
                appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
            }
            val retry = try {
                runPatchAttempt(provider, retryPrompt.trimEnd(), body)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            val retryAccepted = validatePatchAttempt(retry)
            if (retryAccepted != null) {
                return AgentPatchCascadeResult(success = retryAccepted.copy(retryAttempted = true))
            }

            lastFailure = buildPatchFailure(retry, retryAttempted = true)
        }

        return AgentPatchCascadeResult(failure = lastFailure)
    }

    private fun runPatchAttempt(provider: String, prompt: String, context: String): ProviderCascadeResult =
        router.completeWithCascade(
            requestedProvider = provider,
            prompt = prompt,
            context = context,
            providerOrderOverride = listOf(provider),
            beforeAttempt = { candidate -> authorizeProvider(candidate, prompt, "patch") }
        )

    private fun validatePatchAttempt(
        result: ProviderCascadeResult,
        retryAttempted: Boolean = false
    ): AgentPatchAttempt? {
        val extraction = patchExtractor.extract(result.response) ?: return null
        if (!extraction.hasHunkBody) return null
        if (patchExtractor.validate(extraction.diff) != null) return null
        return AgentPatchAttempt(result, extraction, retryAttempted)
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
}
