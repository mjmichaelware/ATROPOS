package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.provider.ProviderTruthService
import atropos.core.security.RedactionFilter
import atropos.core.provider.ProviderOnboardingService

class AgentService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val router: ProviderCascadeRouter = ProviderCascadeRouter(
        ProviderFactory(config),
        healthyProviderIds = { onboarding.healthyProviderIds() },
        preferredProviderIds = { onboarding.preferredProviderIds() },
        localOnly = { config.runtime.localOnly }
    ),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor(),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val jobStore: AgentJobStore = AgentJobStore(collector.repoRoot),
    private val providerTruthService: ProviderTruthService = ProviderTruthService(config, onboarding = onboarding),
    private val verificationStore: AgentVerificationStore = AgentVerificationStore(collector.repoRoot),
    private val verifier: AgentVerifier = AgentVerifier(config, collector, patchStore, verificationStore),
    private val repairService: AgentRepairService = AgentRepairService(
        config = config,
        collector = collector,
        onboarding = onboarding,
        router = router,
        selector = selector,
        patchStore = patchStore,
        verificationStore = verificationStore,
        patchExtractor = patchExtractor
    ),
    private val queueService: AgentQueueService = AgentQueueService(config, collector, onboarding = onboarding),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(
        ExecutionPolicyEngine(collector.repoRoot, localOnly = config.runtime.localOnly)
    ),
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
    private val policyEnforcer = AgentPolicyEnforcer(agencyGate)
    private val askHandler = AgentAskHandler(
        router = router,
        selector = selector,
        collector = collector,
        jobStore = jobStore,
        queueService = queueService,
        memoryStore = memoryStore,
        providerTruthService = providerTruthService,
        redactionFilter = redactionFilter,
        answers = answers,
        failureSummary = failureSummary,
        attestationRetry = attestationRetry,
        enforceProviderPolicy = ::enforceProviderPolicy
    )
    private val patchHandler = AgentPatchHandler(
        collector = collector,
        selector = selector,
        patchStore = patchStore,
        queueService = queueService,
        memoryStore = memoryStore,
        redactionFilter = redactionFilter,
        patchCascadeRunner = patchCascadeRunner,
        failureSummary = failureSummary
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

    fun ask(
        activeProviderName: String,
        task: String,
        contextOverride: AgentAskContextOverride? = null
    ): AgentRunResult = askHandler.handle(activeProviderName, task, contextOverride)

    fun patch(activeProviderName: String, task: String, patchProviderOverride: String? = null): AgentPatchRunResult =
        patchHandler.handle(activeProviderName, task, patchProviderOverride)

    fun verify(patchReference: String): AgentVerificationRunResult =
        verifier.verify(patchReference)

    private fun AgentAskContextOverride.toSnapshot(repoRoot: java.nio.file.Path): AgentContextSnapshot =
        AgentContextSnapshot(
            repoRoot = repoRoot,
            text = contextText,
            byteCount = byteCount,
            truncated = false,
            sourcePackId = sourcePackId,
            fetchReceiptId = fetchReceiptId,
            sourcePackContentHash = sourcePackContentHash,
            sourceTreeHash = sourceTreeHash,
            sourceBindingKind = sourceBindingKind
        )

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
        policyEnforcer.enforce(provider, prompt, operation)
    }

}
