package atropos.core.provider.adapter

import atropos.core.agent.AgentQueueService
import atropos.core.provider.ApiCapability
import atropos.core.provider.AtroposCostPolicy
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.ProviderTask
import atropos.core.provider.ProviderTaskClassifier
import atropos.core.provider.ProviderTaskKind
import atropos.core.provider.QuotaLedger
import atropos.core.provider.RoutePolicy
import atropos.core.provider.RoutePolicyDecision
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.paid.EmergencyPaidGate
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ProviderActionProposals
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderFailure
import java.util.Locale

data class AdapterRouteResult(
    val prompt: String,
    val decision: RoutePolicyDecision,
    val adapterStatus: AdapterStatus?,
    val dryRunResult: ProviderCallResult?,
    val note: String
) {
    val selectedProviderId: String? get() = decision.selectedProviderId
}

class AdapterRouteFacade(
    private val descriptorRegistry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val adapterRegistry: ProviderAdapterRegistry = StaticProviderAdapterRegistry(descriptorRegistry),
    private val ledger: QuotaLedger = InMemoryQuotaLedger(
        FileQuotaLedger.seedFromDescriptors(descriptorRegistry)
    ),
    private val costPolicy: AtroposCostPolicy = AtroposCostPolicy.FREE_ONLY,
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate()
) {
    private val classifier = ProviderTaskClassifier()
    private val agencyGate = BoundedAgencyGate()
    private val unavailableQueue by lazy { AgentQueueService() }

    fun decide(prompt: String, dryRun: Boolean = true): AdapterRouteResult {
        val task = classifier.classify(prompt)
        return decideWithPolicy(task, prompt, dryRun)
    }

    fun decide(task: ProviderTask, prompt: String = task.prompt, dryRun: Boolean = true): AdapterRouteResult {
        return decideWithPolicy(task, prompt, dryRun)
    }

    private fun decideWithPolicy(task: ProviderTask, prompt: String, dryRun: Boolean): AdapterRouteResult {
        val policyDecision = RoutePolicy(
            registry = descriptorRegistry,
            ledger = ledger,
            costPolicy = costPolicy,
            paidGate = paidGate
        ).decide(task)

        val adapter = policyDecision.selectedProviderId?.let {
            adapterRegistry.getByProviderId(it)
        }

        val status = adapter?.status()
        val result = when {
            adapter != null -> completeThroughAgency(adapter, task, prompt, dryRun, "route")
            policyDecision.queued -> queuedResult(task, policyDecision, persist = !dryRun)
            else -> null
        }

        val note = when {
            adapter == null -> "no adapter selected; local degraded mode"
            status?.implemented == true && status.dryRunOnly -> "adapter kernel ready; fixture lane active"
            status?.implemented == true -> "adapter implemented; live tests opt-in"
            else -> "descriptor registered; provider-specific adapter pending"
        }

        return AdapterRouteResult(
            prompt = prompt,
            decision = policyDecision,
            adapterStatus = status,
            dryRunResult = result,
            note = note
        )
    }

    private fun completeThroughAgency(
        adapter: ProviderAdapter,
        task: ProviderTask,
        prompt: String,
        dryRun: Boolean,
        operation: String
    ): ProviderCallResult {
        val proposal = ProviderActionProposals.forCall(
            provider = adapter.providerId,
            operation = operation,
            promptLength = prompt.length,
            actor = ActionActor.SystemService("provider-route")
        ).copy(paidProvider = ProviderActionProposals.isPaid(adapter.providerId) && !paidGate.isProviderUnlocked(adapter.providerId))
        val decision = agencyGate.evaluate(proposal)
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = adapter.providerId,
                    type = NormalizedProviderFailureType.INTERNAL,
                    cleanSummary = "provider call refused by policy: ${decision.reason}",
                    terminal = true
                )
            )
        }
        return adapter.complete(AdapterRequest(task = task, prompt = prompt, dryRun = dryRun))
    }

    private fun queuedResult(
        task: ProviderTask,
        decision: RoutePolicyDecision,
        persist: Boolean
    ): ProviderCallResult.Queued {
        val now = System.currentTimeMillis()
        val retryAt = decision.skipped.mapNotNull { eligibility ->
            eligibility.quota?.cooldownUntilEpochMs ?: eligibility.quota?.resetAtEpochMs
        }.filter { it > now }.minOrNull() ?: now + 60_000L
        val queueRecord = if (persist) {
            unavailableQueue.enqueueUnavailable(task.prompt, retryAt)
        } else {
            null
        }
        val baseReason = decision.queueReason ?: "no eligible provider; local queue/degraded route"
        val reason = if (persist && queueRecord == null) {
            "$baseReason; queue persistence unavailable"
        } else {
            baseReason
        }
        return ProviderCallResult.Queued(
            task = task,
            earliestRetryEpochMs = retryAt,
            reason = reason,
            queueRecordId = queueRecord?.id
        )
    }

    private fun completeThroughAgency(
        adapter: ProviderAdapter,
        task: ProviderTask,
        prompt: String,
        dryRun: Boolean,
        operation: String
    ): ProviderCallResult {
        val proposal = ProviderActionProposals.forCall(
            provider = adapter.providerId,
            operation = operation,
            promptLength = prompt.length,
            actor = ActionActor.SystemService("provider-route")
        ).copy(paidProvider = ProviderActionProposals.isPaid(adapter.providerId) && !paidGate.isProviderUnlocked(adapter.providerId))
        val decision = agencyGate.evaluate(proposal)
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = adapter.providerId,
                    type = NormalizedProviderFailureType.INTERNAL,
                    cleanSummary = "provider call refused by policy: ${decision.reason}",
                    terminal = true
                )
            )
        }
        return adapter.complete(AdapterRequest(task = task, prompt = prompt, dryRun = dryRun))
    }

    fun adapterStatus(): List<AdapterStatus> =
        adapterRegistry.status().sortedWith(compareBy({ it.providerId }))

    fun renderRoute(prompt: String): String {
        val result = decide(prompt, dryRun = true)
        val decision = result.decision
        val out = mutableListOf<String>()

        out += "route: ${decision.task.kind.name.lowercase(Locale.US)} -> ${decision.selectedProviderId ?: "local_degraded"}"
        out += "capability: ${decision.task.capability.name.lowercase(Locale.US)}"
        out += "policy: ${costPolicy.name.lowercase(Locale.US)}"
        out += "adapter: ${result.adapterStatus?.providerId ?: "none"}"
        out += "adapter health: ${result.adapterStatus?.health ?: "none"}"
        out += "note: ${result.note}"

        out += "eligible:"
        if (decision.eligible.isEmpty()) {
            out += "  none"
        } else {
            decision.eligible.take(10).forEach {
                out += "  ${it.provider.id} reason=${it.reason} state=${it.quota?.state?.name?.lowercase(Locale.US) ?: "quota_unknown"}"
            }
        }

        out += "skipped:"
        if (decision.skipped.isEmpty()) {
            out += "  none"
        } else {
            decision.skipped.take(14).forEach {
                out += "  ${it.provider.id} reason=${it.reason} state=${it.quota?.state?.name?.lowercase(Locale.US) ?: "quota_unknown"}"
            }
        }

        return out.joinToString("\n")
    }

    private fun ProviderDescriptor.toRecord(): ProviderQuotaRecord =
        ProviderQuotaRecord(
            providerId = id,
            costMode = costMode,
            quotaWeight = quotaTier,
            configured = isLocal,
            verified = isLocal,
            state = if (isLocal) ProviderAvailabilityState.READY else ProviderAvailabilityState.UNKNOWN,
            paidLocked = isPaidLocked()
        )
}
