package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ProviderActionProposals
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ProviderTruthService
import atropos.core.security.RedactionFilter

class AgentService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val router: ProviderCascadeRouter = ProviderCascadeRouter(ProviderFactory(config)),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val jobStore: AgentJobStore = AgentJobStore(collector.repoRoot),
    private val providerTruthService: ProviderTruthService = ProviderTruthService(config),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val verifier: AgentVerifier = AgentVerifier(config, collector, patchStore, verificationStore),
    private val repairService: AgentRepairService = AgentRepairService(config, collector, router, selector, patchStore, verificationStore, patchExtractor),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(collector.repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile()),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val patchCascadeRunner = AgentPatchCascadeRunner(
        router = router,
        patchExtractor = patchExtractor,
        redactionFilter = redactionFilter,
        repoRoot = collector.repoRoot,
        memoryStore = memoryStore,
        authorizeProvider = ::enforceProviderPolicy
    )

    fun status(activeProviderName: String): AgentStatusSnapshot {
        val selection = selector.select(activeProviderName)
        val lastActualProvider = jobStore.latest()?.provider?.takeIf { it.isNotBlank() }
        val truth = providerTruthService.snapshot(activeProviderName, lastActualProvider)
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
            knownActiveProviders = selection.knownActiveProviders,
            providerTruthReport = truth.renderInventory()
        )
    }

    fun ask(activeProviderName: String, task: String): AgentRunResult {
        val snapshot = collector.collect()
        val selection = selector.select(activeProviderName)
        val sanitizedTask = redactionFilter.redact(task.trim())
        val providerId = selection.askOrder.firstOrNull() ?: "groq"
        return try {
            val result = router.completeWithCascade(
                requestedProvider = providerId,
                prompt = sanitizedTask,
                context = AgentPromptContract.build(
                    context = snapshot.text,
                    providerId = providerId,
                    task = sanitizedTask,
                    repoRoot = collector.repoRoot
                ),
                providerOrderOverride = selection.askOrder,
                beforeAttempt = { provider -> enforceProviderPolicy(provider, sanitizedTask, "ask") }
            )

            // Build the envelope that was sent for attestation verification
            val envelope = ContextEnvelopeFactory.createSimple(
                providerId = result.providerName,
                modelId = "",
                task = sanitizedTask,
                repoRoot = collector.repoRoot
            )

            // Verify provider response attestation
            val verified = ContextAttestationService.verify(envelope, result.response)
            val displayText: String
            val providerDisplayName: String
            when (verified) {
                is ContextAttestationService.VerifiedResult.Accepted -> {
                    displayText = verified.cleanedResponse
                    providerDisplayName = result.providerName
                }
                is ContextAttestationService.VerifiedResult.Rejected -> {
                    // Persist the context failure and fall back
                    memoryStore.rememberFailure(
                        subjectType = "context_failure",
                        subjectId = null,
                        title = verified.failure.javaClass.simpleName,
                        body = "${verified.failure.providerId}: ${verified.failure.reason}",
                        tags = listOf("context", "attestation", "failure")
                    )
                    // Try once more with corrective compact context, then fall back to local
                    val retryResult = retryWithCompactContext(providerId, sanitizedTask, snapshot.text)
                    if (retryResult != null) {
                        displayText = redactionFilter.redact(normalizeAgentAnswer(retryResult.response.trim()))
                        providerDisplayName = retryResult.providerName
                    } else {
                        displayText = fallbackAnswer(sanitizedTask, snapshot)
                        providerDisplayName = "local_fallback"
                    }
                }
            }

            memoryStore.rememberRoute(
                subjectId = providerDisplayName,
                title = "agent ask route",
                body = "task=$sanitizedTask\nprovider=$providerDisplayName",
                tags = listOf("agent", "ask", "route")
            )

            AgentRunResult(
                providerName = providerDisplayName,
                answerText = redactionFilter.redact(normalizeAgentAnswer(displayText.trim())),
                contextByteCount = snapshot.byteCount
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask failed",
                body = compactFailureSummary(failure.message),
                tags = listOf("agent", "ask", "failure")
            )
            AgentRunResult(
                providerName = "local_fallback",
                answerText = fallbackAnswer(sanitizedTask, snapshot),
                contextByteCount = snapshot.byteCount,
                failureSummary = compactFailureSummary(failure.message)
            )
        }
    }

    /**
     * Retry a provider call with compact corrective context after an
     * attestation failure.
     */
    private fun retryWithCompactContext(
        providerId: String,
        sanitizedTask: String,
        context: String
    ): ProviderCascadeResult? {
        return try {
            val compactContext = "ATROPOS: retrying after context attestation failure. " +
                "The previous response was rejected. You are operating inside the ATROPOS software " +
                "engine. ATROPOS refers to this repository and runtime, not Greek mythology. " +
                "Include the required attestation block in your response.\n\n" + context
            router.completeWithCascade(
                requestedProvider = providerId,
                prompt = sanitizedTask,
                context = AgentPromptContract.build(
                    context = compactContext,
                    providerId = providerId,
                    task = sanitizedTask,
                    repoRoot = collector.repoRoot
                ),
                beforeAttempt = { provider -> enforceProviderPolicy(provider, sanitizedTask, "ask") }
            ).let { result ->
                val retryEnvelope = ContextEnvelopeFactory.createSimple(
                    providerId = result.providerName,
                    modelId = "",
                    task = sanitizedTask,
                    repoRoot = collector.repoRoot
                )
                val retryVerified = ContextAttestationService.verify(retryEnvelope, result.response)
                when (retryVerified) {
                    is ContextAttestationService.VerifiedResult.Accepted -> result
                    is ContextAttestationService.VerifiedResult.Rejected -> null
                }
            }
        } catch (_: Exception) { null }
    }

    fun patch(activeProviderName: String, task: String, patchProviderOverride: String? = null): AgentPatchRunResult {
        val snapshot = collector.collectPatch(task)
        val selection = selector.select(activeProviderName, patchProviderOverride)
        val prompt = redactionFilter.redact(task.trim())

        return try {
            val cascade = patchCascadeRunner.run(selection.patchOrder, prompt, snapshot.text)
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
            memoryStore.rememberRoute(
                subjectId = result.providerName,
                title = "agent patch route",
                body = "task=${prompt.trim()}\nprovider=${result.providerName}\npatch=${record.id}\ncheck=${check.statusText}",
                tags = listOf("agent", "patch", "route")
            )

            AgentPatchRunResult(
                providerName = result.providerName,
                contextByteCount = snapshot.byteCount,
                diffByteCount = record.diffBytes,
                patchId = record.id,
                patchPath = record.diffFile,
                checkResult = check
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_patch",
                subjectId = null,
                title = "agent patch failed",
                body = compactFailureSummary(failure.message),
                tags = listOf("agent", "patch", "failure")
            )
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

    private fun fallbackAnswer(task: String, snapshot: AgentContextSnapshot): String = buildString {
        appendLine("Yes. ATROPOS supplied repo context, so I can see the workspace through that bounded snapshot.")
        appendLine("I do not have direct filesystem access, but the collected context includes git status, a shallow tree, and selected provider/routing/agent source files.")
        appendLine("I can use this context to reason about the code, draft a patch, or inspect a specific file next.")
        appendLine("Task: ${redactionFilter.redact(task.trim().ifBlank { "(blank task)" })}")
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
            ?.let { redactionFilter.compact(it, 240) }
            ?: "provider cascade failed"

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

    /**
     * The provider call is proposed, not performed: the gate decides, and a
     * refusal throws before any prompt leaves the process.
     */
    private fun enforceProviderPolicy(provider: String, prompt: String, operation: String) {
        val decision = agencyGate.evaluate(
            // The operator initiated this; the provider performs the work but
            // is not the actor.
            ProviderActionProposals.forCall(provider, operation, prompt.length, ActionActor.HumanOwner)
        )
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }
}
