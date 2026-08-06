package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeResult
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ProviderActionProposals
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter

/**
 * Asks a provider to fix the change that failed its own verification.
 *
 * The repair path is narrow on purpose: it starts from a stored patch whose
 * verification is on record as failed, and it refuses everything else. A repair
 * with no failed verification behind it would be an unprompted rewrite of code
 * that was never shown to be broken.
 *
 * ## What this file no longer owns
 *
 * It used to carry private copies of the patch attempt model, the response
 * validator, the failure builders, and the attestation check — the same four
 * things [AgentPatchCascadeRunner] had. Both now compose the shared owners
 * ([AgentPatchResponseValidator], [AgentPatchAttemptFactory],
 * [AgentPatchAttestationGate]), so "what counts as a usable diff" has one
 * answer instead of two that could drift apart.
 *
 * The cascade loop itself is still separate from the runner's, because the two
 * differ in a way that matters: this one attests against a stable task string
 * derived from the patch id, so a retry does not invalidate its own attestation.
 */
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
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val queueService: AgentQueueService = AgentQueueService(config, collector)
) {
    private val validator = AgentPatchResponseValidator(patchExtractor)
    private val attempts = AgentPatchAttemptFactory(patchExtractor, validator, redactionFilter)
    private val attestation = AgentPatchAttestationGate()

    /**
     * Whether a repair would refuse, without contacting any provider.
     *
     * Returns null when a repair would proceed. Lets a caller show the refusal
     * before spending a provider call on a request that cannot succeed.
     */
    fun previewRepair(patchReference: String): AgentPatchRunResult? {
        val patch = patchStore.resolvePatchSnapshot(patchReference)
            ?: return AgentPatchRunResultFactory.noRepairTarget(patchId = null)
        val verification = verificationStore.latestRecord(patch.id)
            ?: return AgentPatchRunResultFactory.noRepairTarget(patchId = patch.id)
        if (verification.passed) return AgentPatchRunResultFactory.noRepairTarget(patchId = patch.id)
        return null
    }

    fun repair(activeProviderName: String, patchReference: String): AgentPatchRunResult {
        val patch = patchStore.resolvePatchSnapshot(patchReference)
            ?: return AgentPatchRunResultFactory.missingPatch(patchReference)

        val verification = verificationStore.latestRecord(patch.id)
            ?: return AgentPatchRunResultFactory.noRepairTarget(patch.id)
        if (verification.passed) return AgentPatchRunResultFactory.noRepairTarget(patch.id)

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

        AgentProviderContextBoundary.validateSourcePack(
            context = contextSnapshot.text,
            sourcePackId = contextSnapshot.sourcePackId,
            fetchReceiptId = contextSnapshot.fetchReceiptId,
            sourcePackContentHash = contextSnapshot.sourcePackContentHash,
            sourceTreeHash = contextSnapshot.sourceTreeHash,
            sourceBindingKind = contextSnapshot.sourceBindingKind,
            truncated = contextSnapshot.truncated
        )?.let { refusal ->
            return refuseForSourcePack(patch.id, refusal.message, contextSnapshot.byteCount)
        }

        return runRepairCascade(
            patchOrder = selection.patchOrder,
            repairContext = AgentRepairPromptContext(
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
            sourceVerificationId = verification.id
        )
    }

    private fun refuseForSourcePack(
        patchId: String,
        reason: String,
        contextByteCount: Int
    ): AgentPatchRunResult {
        memoryStore.rememberFailure(
            subjectType = "agent_repair",
            subjectId = patchId,
            title = "agent repair source context unavailable",
            body = reason,
            tags = listOf("agent", "repair", "source-pack", "blocked")
        )
        return AgentPatchRunResultFactory.localFailure(
            providerName = "local_fallback",
            contextByteCount = contextByteCount,
            retryAttempted = false,
            failureSummary = reason,
            rejectionReason = reason
        )
    }

    /**
     * Stores the repaired diff and records the route that produced it.
     *
     * The `git apply --check` result is written into the patch metadata before
     * this returns, so a repair that cannot be applied is on record as such
     * rather than being discovered at apply time.
     */
    private fun runRepairCascade(
        patchOrder: List<String>,
        repairContext: AgentRepairPromptContext,
        contextByteCount: Int,
        sourcePackId: String?,
        fetchReceiptId: String?,
        sourceVerificationId: String
    ): AgentPatchRunResult {
        val cascade = runPatchCascade(patchOrder, repairContext)
        val queued = cascade.failure?.result?.takeIf { it.queued }
        if (queued != null) {
            val task = "repair patch ${repairContext.patchId}"
            val queueRecord = runCatching { queueService.enqueue(task) }.getOrNull()
            val reason = queued.queueReason ?: "all repair providers unavailable"
            val queueMessage = queueRecord?.let { "repair queued as ${it.id}" }
                ?: "repair deferred; local queue persistence unavailable"
            memoryStore.rememberRepair(
                subjectId = queueRecord?.id ?: repairContext.patchId,
                title = "agent repair queued after provider exhaustion",
                body = "verification=$sourceVerificationId\nreason=$reason\n$queueMessage",
                tags = listOf("agent", "repair", "queue", "degraded")
            )
            return AgentPatchRunResultFactory.localFailure(
                providerName = "local_queue",
                contextByteCount = contextByteCount,
                retryAttempted = false,
                failureSummary = reason,
                rejectionReason = queueMessage
            )
        }
        val acceptance = cascade.success ?: return AgentPatchRunResultFactory.localFailure(
            providerName = cascade.failure?.result?.providerName
                ?: patchOrder.firstOrNull()
                ?: "local_fallback",
            contextByteCount = contextByteCount,
            retryAttempted = cascade.failure?.retryAttempted ?: false,
            failureSummary = cascade.failure?.rejectionReason
                ?: "provider response did not include a usable unified diff",
            rejectionReason = cascade.failure?.rejectionReason,
            responsePreview = cascade.failure?.responsePreview
        )

        val record = patchStore.createRecord(
            provider = acceptance.result.providerName,
            task = "repair from verification $sourceVerificationId",
            contextBytes = contextByteCount,
            diff = acceptance.extraction.diff
        )
        val check = patchStore.runGitApplyCheck(record.diffFile)
        patchStore.writeMeta(record, check)
        memoryStore.rememberRepair(
            subjectId = record.id,
            title = "agent repair route",
            body = buildString {
                appendLine("verification=$sourceVerificationId")
                appendLine("provider=${acceptance.result.providerName}")
                appendLine("patch=${record.id}")
                appendLine("check=${check.statusText}")
                appendLine("sourcePack=${sourcePackId ?: "none"}")
                append("fetchReceipt=${fetchReceiptId ?: "none"}")
            },
            tags = listOf("agent", "repair", "route")
        )

        return AgentPatchRunResult(
            providerName = acceptance.result.providerName,
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
        repairContext: AgentRepairPromptContext
    ): AgentPatchCascadeResult {
        var lastFailure: AgentPatchAttempt? = null

        for (provider in patchOrder) {
            val initial = try {
                runPatchAttempt(provider, REPAIR_PROMPT, repairContext)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = false)
                continue
            }
            accept(initial, retryAttempted = false)
                ?.let { return AgentPatchCascadeResult(success = it) }

            val retry = try {
                runPatchAttempt(provider, retryPrompt(), repairContext)
            } catch (failure: Exception) {
                lastFailure = attempts.exceptionFailure(provider, failure, retryAttempted = true)
                continue
            }
            accept(retry, retryAttempted = true)
                ?.let { return AgentPatchCascadeResult(success = it) }

            lastFailure = attempts.patchFailure(retry, retryAttempted = true)
        }

        return AgentPatchCascadeResult(failure = lastFailure)
    }

    /**
     * Turns a repair response into an accepted attempt, or null.
     *
     * Attestation runs first and its refusal is recorded. Repair was the one
     * live path where model output became a repository mutation without its
     * response ever being checked against its envelope; refusing here means the
     * patch is never stored rather than being rejected further downstream.
     */
    private fun accept(
        result: ProviderCascadeResult,
        retryAttempted: Boolean
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

    private fun retryPrompt(): String = buildString {
        appendLine(REPAIR_PROMPT)
        appendLine()
        appendLine(
            "Your previous response was rejected because no unified diff was found. " +
                "Return ONLY a valid unified diff for the same task."
        )
        appendLine("Include file headers, at least one @@ hunk header, and the added or removed line(s).")
    }.trimEnd()

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

    private companion object {
        const val REPAIR_PROMPT = "Repair the verification failure by returning only a unified diff."
    }
}
