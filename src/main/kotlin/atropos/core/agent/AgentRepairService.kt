package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ProviderActionProposals
import atropos.core.security.RedactionFilter

class AgentRepairService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val router: ProviderCascadeRouter = ProviderCascadeRouter(ProviderFactory(config)),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(collector.repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile()),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun previewRepair(patchReference: String): AgentPatchRunResult? {
        val patch = patchStore.resolvePatchSnapshot(patchReference)
            ?: return noRepairTarget(patchId = null)

        val verification = verificationStore.latestRecord(patch.id)
            ?: return noRepairTarget(patchId = patch.id)

        if (verification.passed) {
            return noRepairTarget(patchId = patch.id)
        }

        return null
    }

    fun repair(activeProviderName: String, patchReference: String): AgentPatchRunResult {
        val patch = patchStore.resolvePatchSnapshot(patchReference)
            ?: return AgentPatchRunResult(
                providerName = "none",
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
            ?: return noRepairTarget(patch.id)

        if (verification.passed) {
            return noRepairTarget(patch.id)
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
        val sourceRefusal = AgentProviderContextBoundary.validateSourcePack(
            context = contextSnapshot.text,
            sourcePackId = contextSnapshot.sourcePackId,
            fetchReceiptId = contextSnapshot.fetchReceiptId
        )
        if (sourceRefusal != null) {
            val reason = sourceRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_repair",
                subjectId = patch.id,
                title = "agent repair source context unavailable",
                body = reason,
                tags = listOf("agent", "repair", "source-pack", "blocked")
            )
            return localPatchFailure(
                providerName = "local_fallback",
                contextByteCount = contextSnapshot.byteCount,
                retryAttempted = false,
                failureSummary = reason,
                rejectionReason = reason
            )
        }

        return runRepairCascade(
            patchOrder = selection.patchOrder,
            prompt = "Repair the verification failure by returning only a unified diff.",
            repairContext = RepairPromptContext(
                patchId = patch.id,
                changedPaths = patch.extraction.touchedPaths,
                failedCommand = verification.command,
                exitCode = verification.exitCode,
                durationMillis = verification.durationMillis,
                stdout = verification.stdout,
                stderr = verification.stderr,
                context = contextSnapshot.text
            ),
            contextByteCount = contextSnapshot.byteCount,
            sourcePackId = contextSnapshot.sourcePackId,
            fetchReceiptId = contextSnapshot.fetchReceiptId,
            sourceVerificationId = verification.id,
            noRepairMessage = "no failed verification to repair.",
            patchId = patch.id
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

    private data class RepairPromptContext(
        val patchId: String,
        val changedPaths: List<String>,
        val failedCommand: String,
        val exitCode: Int?,
        val durationMillis: Long,
        val stdout: String,
        val stderr: String,
        val context: String
    ) {
        val attestationTask: String get() = "repair patch $patchId"
    }

    private fun runRepairCascade(
        patchOrder: List<String>,
        prompt: String,
        repairContext: RepairPromptContext,
        contextByteCount: Int,
        sourcePackId: String?,
        fetchReceiptId: String?,
        sourceVerificationId: String,
        noRepairMessage: String,
        patchId: String
    ): AgentPatchRunResult {
        val cascade = runPatchCascade(patchOrder, prompt, repairContext, sourceVerificationId, patchId)
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
        memoryStore.rememberRepair(
            subjectId = record.id,
            title = "agent repair route",
            body = "verification=$sourceVerificationId\nprovider=${result.providerName}\npatch=${record.id}\ncheck=${check.statusText}\nsourcePack=${sourcePackId ?: "none"}\nfetchReceipt=${fetchReceiptId ?: "none"}",
            tags = listOf("agent", "repair", "route")
        )

        return AgentPatchRunResult(
            providerName = result.providerName,
            contextByteCount = contextByteCount,
            diffByteCount = record.diffBytes,
            patchId = record.id,
            patchPath = record.diffFile,
            checkResult = check,
            retryAttempted = acceptance.retryAttempted,
            sourceVerificationId = sourceVerificationId,
            sourcePackId = sourcePackId,
            fetchReceiptId = fetchReceiptId
        )
    }

    private fun runPatchCascade(
        patchOrder: List<String>,
        prompt: String,
        repairContext: RepairPromptContext,
        sourceVerificationId: String,
        patchId: String
    ): PatchCascadeResult {
        var lastFailure: PatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, prompt, repairContext, patchId)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            val accepted = validatePatchAttempt(initial, task = repairContext.attestationTask)
            if (accepted != null) return PatchCascadeResult(success = accepted)

            val retryPrompt = buildString {
                appendLine(prompt)
                appendLine()
                appendLine("Your previous response was rejected because no unified diff was found. Return ONLY a valid unified diff for the same task.")
                appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
            }
            val retry = try {
                runPatchAttempt(provider, retryPrompt.trimEnd(), repairContext, patchId)
            } catch (failure: Exception) {
                lastFailure = buildExceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            val retryAccepted = validatePatchAttempt(retry, task = repairContext.attestationTask)
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
        repairContext: RepairPromptContext,
        patchId: String
    ): atropos.core.ProviderCascadeResult {
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
            beforeAttempt = { candidate -> enforceProviderPolicy(candidate, prompt, patchId) },
            contextEnvelope = envelope
        )
    }

    /**
     * Turns a repair response into a patch, but only if the provider proved it
     * answered against the context it was given.
     *
     * Repair was the one live path where model output became a repository
     * mutation without its response ever being checked against its envelope. An
     * unattested repair is refused here rather than downstream, so the patch is
     * never stored.
     */
    private fun validatePatchAttempt(
        result: atropos.core.ProviderCascadeResult,
        retryAttempted: Boolean = false,
        task: String = ""
    ): PatchAttempt? {
        if (!attested(result, task)) return null

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

    /**
     * Verifies the response against the envelope the call was made under.
     *
     * A rejection is persisted as a typed context failure, the same record
     * `AgentService` writes, so drift on the repair path is as visible as drift
     * anywhere else.
     */
    private fun attested(result: atropos.core.ProviderCascadeResult, task: String): Boolean {
        val envelope = result.contextEnvelope ?: return false
        return when (val verified = ContextAttestationService.verify(envelope, result.response)) {
            is ContextAttestationService.VerifiedResult.Accepted -> true
            is ContextAttestationService.VerifiedResult.Rejected -> {
                memoryStore.rememberFailure(
                    subjectType = "context_failure",
                    subjectId = null,
                    title = verified.failure.javaClass.simpleName,
                    body = "repair ${verified.failure.providerId}: ${verified.failure.reason}",
                    tags = listOf("context", "attestation", "failure", "repair")
                )
                false
            }
        }
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
            responsePreview = redactionFilter.redact(patchExtractor.preview(result.response))
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

    /**
     * The repair provider call is proposed, not performed: the gate decides,
     * and a refusal throws before any prompt leaves the process.
     */
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

    private fun PatchAttempt.copy(retryAttempted: Boolean): PatchAttempt =
        PatchAttempt(
            result = result,
            extraction = extraction,
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = responsePreview
        )

    private fun noRepairTarget(patchId: String?): AgentPatchRunResult =
        AgentPatchRunResult(
            providerName = "none",
            contextByteCount = 0,
            diffByteCount = 0,
            patchId = patchId,
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
            ?.let { redactionFilter.compact(it, 240) }
            ?: "provider cascade failed"

    private fun refusalForMissingPatch(reference: String): String =
        if (reference.trim().isBlank()) "no patch id exists"
        else "patch not found: ${reference.trim()}"

}
