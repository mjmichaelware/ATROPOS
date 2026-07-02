package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory

class AgentRepairService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val router: ProviderCascadeRouter = ProviderCascadeRouter(ProviderFactory(config)),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor()
) {
    fun repair(activeProviderName: String, patchReference: String): AgentPatchRunResult {
        val patch = patchStore.resolvePatchSnapshot(patchReference)
            ?: return AgentPatchRunResult(
                providerName = "local_fallback",
                contextByteCount = 0,
                diffByteCount = 0,
                patchId = null,
                patchPath = null,
                checkResult = null,
                retryAttempted = false,
                failureSummary = refusalForMissingPatch(patchReference),
                rejectionReason = refusalForMissingPatch(patchReference),
                responsePreview = "",
                message = refusalForMissingPatch(patchReference)
            )

        val verification = verificationStore.latestRecord(patch.id)
            ?: return noRepairTarget(activeProviderName, patch.id)

        if (verification.passed) {
            return noRepairTarget(activeProviderName, patch.id)
        }

        val selection = selector.select(activeProviderName)
        val contextSnapshot = collector.collectRepair(
            taskHint = buildString {
                appendLine("repair patch ${patch.id}")
                appendLine("verification command: ${verification.command}")
                appendLine("verification exit code: ${verification.exitCode ?: "none"}")
                appendLine("verification failure: ${verification.failureReason ?: "unknown"}")
            },
            fileHints = patch.extraction.touchedPaths
        )

        val body = AgentPromptContract.buildRepair(
            patchId = patch.id,
            changedPaths = patch.extraction.touchedPaths,
            failedCommand = verification.command,
            exitCode = verification.exitCode,
            durationMillis = verification.durationMillis,
            stdout = verification.stdout,
            stderr = verification.stderr,
            context = contextSnapshot.text
        )

        return runRepairCascade(
            patchOrder = selection.patchOrder,
            prompt = "Repair the verification failure by returning only a unified diff.",
            body = body,
            contextByteCount = contextSnapshot.byteCount,
            sourceVerificationId = verification.id,
            noRepairMessage = "no failed verification to repair."
        )
    }

    private data class PatchAttempt(
        val result: atropos.core.ProviderCascadeResult,
        val extraction: AgentPatchExtraction,
        val retryAttempted: Boolean,
        val rejectionReason: String? = null,
        val responsePreview: String? = null
    )

    private data class PatchCascadeResult(
        val success: PatchAttempt? = null,
        val failure: PatchAttempt? = null
    )

    private fun runRepairCascade(
        patchOrder: List<String>,
        prompt: String,
        body: String,
        contextByteCount: Int,
        sourceVerificationId: String,
        noRepairMessage: String
    ): AgentPatchRunResult {
        val cascade = runPatchCascade(patchOrder, prompt, body, sourceVerificationId)
        val acceptance = cascade.success ?: return localPatchFailure(
            providerName = cascade.failure?.result?.providerName ?: patchOrder.firstOrNull() ?: "local_fallback",
            contextByteCount = contextByteCount,
            retryAttempted = cascade.failure?.retryAttempted ?: false,
            failureSummary = cascade.failure?.rejectionReason ?: "provider response did not include a usable unified diff",
            rejectionReason = cascade.failure?.rejectionReason,
            responsePreview = cascade.failure?.responsePreview
        )

        val result = acceptance.result
        val extraction = acceptance.extraction

        val record = patchStore.createRecord(
            provider = result.providerName,
            task = "repair from verification $sourceVerificationId",
            contextBytes = contextByteCount,
            diff = extraction.diff
        )
        val check = patchStore.runGitApplyCheck(record.diffFile)
        patchStore.writeMeta(record, check)

        return AgentPatchRunResult(
            providerName = result.providerName,
            contextByteCount = contextByteCount,
            diffByteCount = record.diffBytes,
            patchId = record.id,
            patchPath = record.diffFile,
            checkResult = check,
            retryAttempted = acceptance.retryAttempted,
            sourceVerificationId = sourceVerificationId
        )
    }

    private fun runPatchCascade(
        patchOrder: List<String>,
        prompt: String,
        body: String,
        sourceVerificationId: String
    ): PatchCascadeResult {
        var lastFailure: PatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, prompt, body)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            val accepted = validatePatchAttempt(initial)
            if (accepted != null) return PatchCascadeResult(success = accepted)

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
                return PatchCascadeResult(success = retryAccepted.copy(retryAttempted = true))
            }

            lastFailure = buildPatchFailure(provider, retry, retryAttempted = true)
        }

        return PatchCascadeResult(failure = lastFailure)
    }

    private fun runPatchAttempt(
        provider: String,
        prompt: String,
        context: String
    ): atropos.core.ProviderCascadeResult =
        router.completeWithCascade(
            requestedProvider = provider,
            prompt = prompt,
            context = context,
            providerOrderOverride = listOf(provider)
        )

    private fun validatePatchAttempt(
        result: atropos.core.ProviderCascadeResult,
        retryAttempted: Boolean = false
    ): PatchAttempt? {
        val extraction = patchExtractor.extract(result.response) ?: return null
        if (!extraction.hasHunkBody) {
            return null
        }

        val validationFailure = patchExtractor.validate(extraction.diff)
        if (validationFailure != null) {
            return null
        }

        return PatchAttempt(result, extraction, retryAttempted)
    }

    private fun buildPatchFailure(
        provider: String,
        result: atropos.core.ProviderCascadeResult,
        retryAttempted: Boolean
    ): PatchAttempt {
        val extraction = patchExtractor.extract(result.response)
        val rejectionReason = when {
            extraction == null -> if (containsDiffHeader(result.response)) "diff body missing" else "no unified diff found"
            !extraction.hasHunkBody -> "diff body missing"
            else -> patchExtractor.validate(extraction.diff) ?: "unknown patch rejection"
        }

        return PatchAttempt(
            result = result,
            extraction = extraction ?: AgentPatchExtraction("", emptyList(), false),
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = patchExtractor.preview(result.response)
        )
    }

    private fun buildExceptionFailure(
        provider: String,
        failure: Exception,
        retryAttempted: Boolean
    ): PatchAttempt {
        val message = compactFailureSummary(failure.message)
        return PatchAttempt(
            result = atropos.core.ProviderCascadeResult(providerName = provider, response = "", errors = emptyList()),
            extraction = AgentPatchExtraction("", emptyList(), false),
            retryAttempted = retryAttempted,
            rejectionReason = message,
            responsePreview = message
        )
    }

    private fun containsDiffHeader(text: String): Boolean =
        text.contains("diff --git ") || text.contains("\n--- ") || text.trimStart().startsWith("--- ")

    private fun PatchAttempt.copy(retryAttempted: Boolean): PatchAttempt =
        PatchAttempt(
            result = result,
            extraction = extraction,
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = responsePreview
        )

    private fun noRepairTarget(activeProviderName: String, patchId: String): AgentPatchRunResult =
        AgentPatchRunResult(
            providerName = activeProviderName,
            contextByteCount = 0,
            diffByteCount = 0,
            patchId = null,
            patchPath = null,
            checkResult = null,
            retryAttempted = false,
            failureSummary = "no failed verification to repair.",
            rejectionReason = "no failed verification to repair.",
            responsePreview = "",
            message = "no failed verification to repair."
        )

    private fun localPatchFailure(
        providerName: String,
        contextByteCount: Int,
        retryAttempted: Boolean,
        failureSummary: String,
        rejectionReason: String? = null,
        responsePreview: String? = null
    ): AgentPatchRunResult =
        AgentPatchRunResult(
            providerName = providerName,
            contextByteCount = contextByteCount,
            diffByteCount = 0,
            patchId = null,
            patchPath = null,
            checkResult = null,
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = responsePreview,
            failureSummary = failureSummary,
            message = "ATROPOS did not apply anything. Local fallback cannot generate a provider patch."
        )

    private fun compactFailureSummary(message: String?): String =
        message?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?.let { if (it.length > 240) it.take(237) + "..." else it }
            ?: "provider cascade failed"

    private fun refusalForMissingPatch(reference: String): String =
        if (reference.trim().isBlank()) "no patch id exists"
        else "patch not found: ${reference.trim()}"
}
