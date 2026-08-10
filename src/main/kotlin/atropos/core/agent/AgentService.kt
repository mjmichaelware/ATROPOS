package atropos.core.agent

import atropos.core.AtroposConfig
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
    private val queueService: AgentQueueService = AgentQueueService(config, collector),
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
    private val answers = AgentAskAnswerNormalizer(redactionFilter)
    private val failureSummary = AgentFailureSummary(redactionFilter)
    private val attestationRetry = AgentAskAttestationRetry(router, ::enforceProviderPolicy)

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

    fun ask(
        activeProviderName: String,
        task: String,
        contextOverride: AgentAskContextOverride? = null
    ): AgentRunResult {
        val selection = selector.select(activeProviderName)
        val sanitizedTask = redactionFilter.redact(task.trim())
        val providerId = selection.askOrder.firstOrNull()
            ?: activeProviderName.trim().lowercase().takeIf { it.isNotBlank() }
            ?: providerTruthService.snapshot().selectedProvider.trim().takeIf { it.isNotBlank() }
            ?: ""
        val envelope = contextOverride?.envelope?.let { AgentContextSnapshotAdapter.forProvider(it, providerId) }
            ?: ContextEnvelopeFactory.createSimple(
                providerId = providerId,
                modelId = "",
                task = sanitizedTask,
                repoRoot = collector.repoRoot
            )
        val snapshot = contextOverride?.let { AgentContextSnapshotAdapter.toSnapshot(it, collector.repoRoot) } ?: collector.collect(sanitizedTask)
        val envelopeRefusal = AgentProviderContextBoundary.validateEnvelope(envelope, collector.repoRoot)
        if (envelopeRefusal != null) {
            val reason = envelopeRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask context envelope refused",
                body = reason,
                tags = listOf("agent", "ask", "context", "blocked")
            )
            return AgentRunResult(
                providerName = "none",
                answerText = reason,
                contextByteCount = snapshot.byteCount,
                failureSummary = reason,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
        val sourceContextRefusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = sanitizedTask,
            sourcePackId = snapshot.sourcePackId,
            fetchReceiptId = snapshot.fetchReceiptId,
            sourcePackContentHash = snapshot.sourcePackContentHash,
            sourceTreeHash = snapshot.sourceTreeHash,
            sourceBindingKind = snapshot.sourceBindingKind,
            context = snapshot.text,
            truncated = snapshot.truncated
        )
        if (sourceContextRefusal != null) {
            val reason = sourceContextRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask source context refused: ${sourceContextRefusal.code}",
                body = reason,
                tags = listOf("agent", "ask", "source-pack", "blocked")
            )
            return AgentRunResult(
                providerName = "local_fallback",
                answerText = "$reason\n${answers.fallbackAnswer(sanitizedTask, snapshot)}",
                contextByteCount = snapshot.byteCount,
                failureSummary = reason,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
        return try {
            val result = router.completeWithCascade(
                requestedProvider = providerId,
                prompt = sanitizedTask,
                context = AgentPromptContract.buildWithEnvelope(
                    context = snapshot.text,
                    envelope = envelope
                ),
                providerOrderOverride = selection.askOrder,
                beforeAttempt = { provider -> enforceProviderPolicy(provider, sanitizedTask, "ask") },
                contextEnvelope = envelope
            )

            if (result.queued) {
                val queueRecord = queueService.enqueueUnavailable(
                    sanitizedTask,
                    retryAtEpochMs = result.earliestRetryEpochMs
                )
                val retryAt = result.earliestRetryEpochMs?.toString() ?: "unknown"
                val queueMessage = if (queueRecord != null) {
                    "all eligible providers unavailable; request queued as ${queueRecord.id} " +
                        "for retry after $retryAt"
                } else {
                    "all eligible providers unavailable; local fallback remains available " +
                        "for retry after $retryAt"
                }
                memoryStore.rememberRoute(
                    subjectId = queueRecord?.id ?: "local_queue",
                    title = "agent ask queued after provider exhaustion",
                    body = "task=$sanitizedTask\nreason=${result.queueReason ?: "provider unavailable"}\nretryAt=$retryAt",
                    tags = listOf("agent", "ask", "queue", "degraded")
                )
                return AgentRunResult(
                    providerName = "local_queue",
                    answerText = queueMessage,
                    contextByteCount = snapshot.byteCount,
                    failureSummary = result.queueReason ?: "provider unavailable",
                    sourcePackId = snapshot.sourcePackId,
                    fetchReceiptId = snapshot.fetchReceiptId
                )
            }

            val verified = ContextAttestationService.verify(envelope, result.response)
            val displayText: String
            val providerDisplayName: String
            val contextAttested: Boolean
            when (verified) {
                is ContextAttestationService.VerifiedResult.Accepted -> {
                    displayText = verified.cleanedResponse
                    providerDisplayName = result.providerName
                    contextAttested = true
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
                    val retryResult = attestationRetry.retry(providerId, sanitizedTask, snapshot.text, envelope)
                    if (retryResult != null) {
                        displayText = answers.present(retryResult.response)
                        providerDisplayName = retryResult.providerName
                    } else {
                        displayText = answers.fallbackAnswer(sanitizedTask, snapshot)
                        providerDisplayName = "local_fallback"
                    }
                    contextAttested = false
                }
            }

            memoryStore.rememberRoute(
                subjectId = providerDisplayName,
                title = "agent ask route",
                body = "task=$sanitizedTask\nprovider=$providerDisplayName\nsourcePack=${snapshot.sourcePackId ?: "none"}\nfetchReceipt=${snapshot.fetchReceiptId ?: "none"}",
                tags = listOf("agent", "ask", "route")
            )

            AgentRunResult(
                providerName = providerDisplayName,
                answerText = answers.present(displayText),
                contextByteCount = snapshot.byteCount,
                contextAttested = contextAttested,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask failed",
                body = failureSummary.compact(failure.message),
                tags = listOf("agent", "ask", "failure")
            )
            AgentRunResult(
                providerName = "local_fallback",
                answerText = answers.fallbackAnswer(sanitizedTask, snapshot),
                contextByteCount = snapshot.byteCount,
                failureSummary = failureSummary.compact(failure.message),
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
    }

    fun patch(activeProviderName: String, task: String, patchProviderOverride: String? = null): AgentPatchRunResult {
        val snapshot = collector.collectPatch(task)
        val selection = selector.select(activeProviderName, patchProviderOverride)
        val prompt = redactionFilter.redact(task.trim())
        val sourceRefusal = AgentProviderContextBoundary.validateSourcePack(
            context = snapshot.text,
            sourcePackId = snapshot.sourcePackId,
            fetchReceiptId = snapshot.fetchReceiptId,
            sourcePackContentHash = snapshot.sourcePackContentHash,
            sourceTreeHash = snapshot.sourceTreeHash,
            sourceBindingKind = snapshot.sourceBindingKind
        )
        if (sourceRefusal != null) {
            val reason = sourceRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_patch",
                subjectId = null,
                title = "agent patch source context unavailable",
                body = reason,
                tags = listOf("agent", "patch", "source-pack", "blocked")
            )
            return AgentPatchRunResultFactory.localFailure(
                providerName = "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = false,
                failureSummary = reason,
                rejectionReason = reason
            )
        }

        return try {
            val cascade = patchCascadeRunner.run(selection.patchOrder, prompt, snapshot.text, snapshot.truncated)
            val queued = cascade.failure?.result?.takeIf { it.queued }
            if (queued != null) {
                val queueRecord = queueService.enqueueUnavailable(
                    prompt,
                    retryAtEpochMs = queued.earliestRetryEpochMs
                )
                val reason = queued.queueReason ?: "all patch providers unavailable"
                val queueMessage = queueRecord?.let { "patch queued as ${it.id}" }
                    ?: "patch deferred; local queue persistence unavailable"
                memoryStore.rememberRoute(
                    subjectId = queueRecord?.id ?: "local_queue",
                    title = "agent patch queued after provider exhaustion",
                    body = "task=$prompt\nreason=$reason\n$queueMessage",
                    tags = listOf("agent", "patch", "queue", "degraded")
                )
                return AgentPatchRunResultFactory.localFailure(
                    providerName = "local_queue",
                    contextByteCount = snapshot.byteCount,
                    retryAttempted = false,
                    failureSummary = reason,
                    rejectionReason = queueMessage
                )
            }
            val acceptance = cascade.success ?: return AgentPatchRunResultFactory.localFailure(
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
                body = "task=${prompt.trim()}\nprovider=${result.providerName}\npatch=${record.id}\ncheck=${check.statusText}\nsourcePack=${snapshot.sourcePackId ?: "none"}\nfetchReceipt=${snapshot.fetchReceiptId ?: "none"}",
                tags = listOf("agent", "patch", "route")
            )

            AgentPatchRunResult(
                providerName = result.providerName,
                contextByteCount = snapshot.byteCount,
                diffByteCount = record.diffBytes,
                patchId = record.id,
                patchPath = record.diffFile,
                checkResult = check,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_patch",
                subjectId = null,
                title = "agent patch failed",
                body = failureSummary.compact(failure.message),
                tags = listOf("agent", "patch", "failure")
            )
            AgentPatchRunResultFactory.localFailure(
                providerName = "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = false,
                failureSummary = failureSummary.compact(failure.message)
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
