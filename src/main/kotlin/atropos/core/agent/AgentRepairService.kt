package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.security.RedactionFilter

/**
 * Asks a provider to fix the change that failed its own verification.
 *
 * The repair path is narrow on purpose: it starts from a stored patch whose
 * verification is on record as failed, and it refuses everything else. A repair
 * with no failed verification behind it would be an unprompted rewrite of code
 * that was never shown to be broken.
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
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val patchValidator = AgentPatchResponseValidator(patchExtractor)
    private val attempts = AgentPatchAttemptFactory(patchExtractor, patchValidator, redactionFilter)
    private val attestation = AgentPatchAttestationGate()
    private val repairValidator = AgentRepairValidator(patchValidator, attestation, memoryStore)
    private val repairCascade = AgentRepairCascade(router, collector, repairValidator, attempts, agencyGate)
    private val repairFinalization = AgentRepairFinalization(memoryStore)

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

        val repairContext = AgentRepairPromptContext(
            patchId = patch.id,
            changedPaths = patch.extraction.touchedPaths,
            failedCommand = verification.command,
            exitCode = verification.exitCode,
            durationMillis = verification.durationMillis,
            stdout = verification.stdout,
            stderr = verification.stderr,
            context = contextSnapshot.text
        )
        val cascade = repairCascade.runCascade(selection.patchOrder, repairContext)
        return repairFinalization.finalizeRepair(
            cascadeResult = cascade,
            repairContext = repairContext,
            contextByteCount = contextSnapshot.byteCount,
            sourcePackId = contextSnapshot.sourcePackId,
            fetchReceiptId = contextSnapshot.fetchReceiptId,
            sourceVerificationId = verification.id,
            patchStore = patchStore
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
}
