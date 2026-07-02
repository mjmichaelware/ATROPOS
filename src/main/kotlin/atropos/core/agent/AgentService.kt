package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import java.nio.file.Path

data class AgentStatusSnapshot(
    val activeProvider: String,
    val providerOrder: List<String>,
    val patchProviderOrder: List<String>,
    val repoRoot: Path,
    val patchDirectory: Path,
    val lastPatchId: String?,
    val contextCapBytes: Int,
    val ownsRepoReadWrite: Boolean,
    val paidAutomaticModeLocked: Boolean,
    val localFallbackEnabled: Boolean,
    val doctorTruthSource: String,
    val knownActiveProviders: List<String>
) {
    fun render(): String = buildString {
        appendLine("agent status:")
        appendLine("  active provider: $activeProvider")
        appendLine("  provider order for /agent ask: ${providerOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  provider order for /agent patch: ${patchProviderOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  repo root: $repoRoot")
        appendLine("  patch directory: $patchDirectory")
        appendLine("  last patch id: ${lastPatchId ?: "none"}")
        appendLine("  context cap bytes: $contextCapBytes")
        appendLine("  repo ownership: ${if (ownsRepoReadWrite) "ATROPOS owns repo read/write; providers only see bounded context" else "unknown"}")
        appendLine("  paid automatic mode: ${if (paidAutomaticModeLocked) "locked" else "unlocked"}")
        appendLine("  local fallback: ${if (localFallbackEnabled) "enabled" else "disabled"}")
        appendLine("  last doctor truth source: $doctorTruthSource")
        appendLine("  known active doctor providers: ${knownActiveProviders.joinToString(", ")}")
    }.trimEnd()
}

data class AgentRunResult(
    val providerName: String,
    val answerText: String,
    val contextByteCount: Int,
    val failureSummary: String? = null
) {
    fun render(): String = buildString {
        appendLine("Provider used: $providerName")
        appendLine("context bytes: $contextByteCount")
        failureSummary?.takeIf { it.isNotBlank() }?.let {
            appendLine("fallback summary: $it")
        }
        appendLine("answer:")
        appendLine(answerText.trimEnd())
    }.trimEnd()
}

data class AgentPatchRunResult(
    val providerName: String,
    val contextByteCount: Int,
    val diffByteCount: Int,
    val patchId: String?,
    val patchPath: Path?,
    val checkResult: AgentPatchCheckResult?,
    val retryAttempted: Boolean = false,
    val rejectionReason: String? = null,
    val responsePreview: String? = null,
    val failureSummary: String? = null,
    val sourceVerificationId: String? = null,
    val message: String? = null
) {
    fun render(): String = buildString {
        appendLine("Patch id: ${patchId ?: "none"}")
        appendLine("Provider used: $providerName")
        appendLine("Context bytes: $contextByteCount")
        appendLine("Diff bytes: $diffByteCount")
        appendLine("Patch path: ${patchPath ?: "none"}")
        appendLine("Retry attempted: ${if (retryAttempted) "yes" else "no"}")
        if (patchId == null) {
            rejectionReason?.takeIf { it.isNotBlank() }?.let { appendLine("Rejection reason: $it") }
            responsePreview?.takeIf { it.isNotBlank() }?.let { appendLine("Response preview: $it") }
        }
        appendLine(
            when (val result = checkResult) {
                null -> "Patch check: NOT RUN"
                else -> {
                    val output = result.output.takeIf { value -> value.isNotBlank() }
                    "Patch check: ${result.statusText}${output?.let { compact -> " :: $compact" } ?: ""}"
                }
            }
        )
        sourceVerificationId?.takeIf { it.isNotBlank() }?.let { appendLine("Source verification: $it") }
        failureSummary?.takeIf { it.isNotBlank() }?.let { appendLine("fallback summary: $it") }
        message?.takeIf { it.isNotBlank() }?.let { appendLine(it.trimEnd()) }
    }.trimEnd()
}

class AgentService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val router: ProviderCascadeRouter = ProviderCascadeRouter(ProviderFactory(config)),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val verifier: AgentVerifier = AgentVerifier(config, collector, patchStore, verificationStore),
    private val repairService: AgentRepairService = AgentRepairService(config, collector, router, selector, patchStore, verificationStore, patchExtractor)
) {
    fun status(activeProviderName: String): AgentStatusSnapshot {
        val selection = selector.select(activeProviderName)
        return AgentStatusSnapshot(
            activeProvider = activeProviderName,
            providerOrder = selection.askOrder,
            patchProviderOrder = selection.patchOrder,
            repoRoot = collector.repoRoot,
            patchDirectory = patchStore.patchDirectory(),
            lastPatchId = patchStore.latestPatchId(),
            contextCapBytes = collector.contextCapBytes,
            ownsRepoReadWrite = true,
            paidAutomaticModeLocked = selection.paidAutomaticModeLocked,
            localFallbackEnabled = selection.localFallbackEnabled,
            doctorTruthSource = selection.doctorTruthSource,
            knownActiveProviders = selection.knownActiveProviders
        )
    }

    fun ask(activeProviderName: String, task: String): AgentRunResult {
        val snapshot = collector.collect()
        val selection = selector.select(activeProviderName)
        return try {
            val result = router.completeWithCascade(
                requestedProvider = selection.askOrder.firstOrNull() ?: "groq",
                prompt = task.trim(),
                context = AgentPromptContract.build(snapshot.text),
                providerOrderOverride = selection.askOrder
            )

            AgentRunResult(
                providerName = result.providerName,
                answerText = normalizeAgentAnswer(result.response.trim()),
                contextByteCount = snapshot.byteCount
            )
        } catch (failure: Exception) {
            AgentRunResult(
                providerName = "local_fallback",
                answerText = fallbackAnswer(task, snapshot),
                contextByteCount = snapshot.byteCount,
                failureSummary = compactFailureSummary(failure.message)
            )
        }
    }

    fun patch(activeProviderName: String, task: String, patchProviderOverride: String? = null): AgentPatchRunResult {
        val snapshot = collector.collectPatch(task)
        val selection = selector.select(activeProviderName, patchProviderOverride)
        val prompt = task.trim()

        return try {
            val cascade = runPatchCascade(selection.patchOrder, prompt, snapshot.text)
            val acceptance = cascade.success ?: return localPatchFailure(
                providerName = cascade.failure?.result?.providerName ?: selection.patchOrder.firstOrNull() ?: "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = cascade.failure?.retryAttempted ?: false,
                failureSummary = cascade.failure?.rejectionReason ?: "provider response did not include a usable unified diff",
                rejectionReason = cascade.failure?.rejectionReason,
                responsePreview = cascade.failure?.responsePreview
            )

            val result = acceptance.result
            val extraction = acceptance.extraction
            val normalizedDiff = patchStore.normalizeProviderDiff(extraction.diff)

            val record = patchStore.createRecord(
                provider = result.providerName,
                task = prompt,
                contextBytes = snapshot.byteCount,
                diff = normalizedDiff
            )
            val check = patchStore.runGitApplyCheck(record.diffFile)
            patchStore.writeMeta(record, check)

            AgentPatchRunResult(
                providerName = result.providerName,
                contextByteCount = snapshot.byteCount,
                diffByteCount = record.diffBytes,
                patchId = record.id,
                patchPath = record.diffFile,
                checkResult = check
            )
        } catch (failure: Exception) {
            localPatchFailure(
                providerName = "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = false,
                failureSummary = compactFailureSummary(failure.message)
            )
        }
    }

    fun verify(patchReference: String): AgentVerificationRunResult =
        verifier.verify(patchReference)

    fun repair(activeProviderName: String, patchReference: String): AgentPatchRunResult =
        repairService.repair(activeProviderName, patchReference)

    fun previewRepair(patchReference: String): AgentPatchRunResult? =
        repairService.previewRepair(patchReference)

    fun applyPatch(
        patchReference: String,
        checkOnly: Boolean,
        verifyAfterApply: Boolean = false
    ): AgentPatchApplyResult {
        val applied = patchStore.applyPatch(patchReference, checkOnly)
        if (checkOnly || !verifyAfterApply || !applied.applied) {
            return applied
        }

        val verification = verifier.verify(applied.patchId ?: patchReference)
        return applied.copy(verificationResult = verification)
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

    private fun fallbackAnswer(task: String, snapshot: AgentContextSnapshot): String = buildString {
        appendLine("Yes. ATROPOS supplied repo context, so I can see the workspace through that bounded snapshot.")
        appendLine("I do not have direct filesystem access, but the collected context includes git status, a shallow tree, and selected provider/routing/agent source files.")
        appendLine("I can use this context to reason about the code, draft a patch, or inspect a specific file next.")
        appendLine("Task: ${task.trim().ifBlank { "(blank task)" }}")
        appendLine("Context bytes: ${snapshot.byteCount}")
    }.trimEnd()

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

    private fun runPatchCascade(
        patchOrder: List<String>,
        prompt: String,
        context: String
    ): PatchCascadeResult {
        val body = AgentPromptContract.buildPatch(context)
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

            val failure = buildPatchFailure(provider, retry, retryAttempted = true)
            lastFailure = failure
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

    private fun normalizeAgentAnswer(answer: String): String {
        val trimmed = answer.trim()
        val hasContextEcho =
            trimmed.contains("\n# Repo Root") ||
                trimmed.contains("\n# Git Status") ||
                trimmed.contains("\n# Selected Sources") ||
                trimmed.contains("Repository context:")

        if (!hasContextEcho) return trimmed

        return "Yes. ATROPOS supplied bounded repo context, so I can reason over the workspace snapshot without direct filesystem access."
    }
}
