package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.AtroposCostPolicy
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ProviderEligibility
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.ProviderTask
import atropos.core.provider.ProviderTaskClassifier
import atropos.core.provider.ProviderTaskKind
import atropos.core.provider.QuotaLedger
import atropos.core.provider.RoutePolicy
import atropos.core.provider.RoutePolicyDecision
import atropos.core.provider.StaticProviderDescriptorRegistry
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
    private val costPolicy: AtroposCostPolicy = AtroposCostPolicy.FREE_ONLY
) {
    private val classifier = ProviderTaskClassifier()

    fun decide(prompt: String, dryRun: Boolean = true): AdapterRouteResult {
        val task = classifier.classify(prompt)
        val researchOverride = researchOverride(task, prompt)
        if (researchOverride != null) return researchOverride
        return decideWithPolicy(task, prompt, dryRun)
    }

    fun decide(task: ProviderTask, prompt: String = task.prompt, dryRun: Boolean = true): AdapterRouteResult {
        val researchOverride = researchOverride(task, prompt)
        if (researchOverride != null) return researchOverride
        return decideWithPolicy(task, prompt, dryRun)
    }

    private fun decideWithPolicy(task: ProviderTask, prompt: String, dryRun: Boolean): AdapterRouteResult {
        val policyDecision = RoutePolicy(
            registry = descriptorRegistry,
            ledger = ledger,
            costPolicy = costPolicy
        ).decide(task)

        val adapter = policyDecision.selectedProviderId?.let {
            adapterRegistry.getByProviderId(it)
        }

        val status = adapter?.status()
        val result = adapter?.complete(
            AdapterRequest(
                task = task,
                prompt = prompt,
                dryRun = dryRun
            )
        )

        val note = when {
            adapter == null -> "no adapter selected; local degraded mode"
            status?.implemented == true && status.dryRunOnly -> "adapter kernel ready; live network opt-in deferred"
            status?.implemented == true -> "adapter implemented"
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

    private fun researchOverride(task: ProviderTask, prompt: String): AdapterRouteResult? {
        if (task.kind != ProviderTaskKind.WEB_DOCS_LOOKUP) return null

        val ordered = listOf("jina", "serpapi", "local")
        val candidates = ordered.mapNotNull { providerId ->
            val adapter = adapterRegistry.getByProviderId(providerId) ?: return@mapNotNull null
            val descriptor = adapter.descriptor
            val quota = ledger.get(providerId) ?: descriptor.toRecord()
            ProviderEligibility(
                provider = descriptor,
                quota = quota,
                eligible = quota.availableAt(System.currentTimeMillis()),
                reason = if (quota.availableAt(System.currentTimeMillis())) "research_priority" else quota.state.name.lowercase(Locale.US)
            )
        }

        val selected = candidates.firstOrNull { it.eligible } ?: candidates.lastOrNull()
        val selectedAdapter = selected?.provider?.id?.let { adapterRegistry.getByProviderId(it) }
        val selectedStatus = selectedAdapter?.status()
        val selectedResult = selectedAdapter?.complete(
            AdapterRequest(
                task = task,
                prompt = prompt,
                dryRun = true
            )
        )

        val decision = RoutePolicyDecision(
            task = task,
            selectedProviderId = selected?.provider?.id,
            selected = selected?.provider,
            eligible = candidates.filter { it.eligible },
            skipped = candidates.filterNot { it.eligible },
            degraded = selected?.provider?.id == "local",
            queued = selected == null,
            queueReason = if (selected == null) "no research adapter available" else null
        )

        return AdapterRouteResult(
            prompt = prompt,
            decision = decision,
            adapterStatus = selectedStatus,
            dryRunResult = selectedResult,
            note = "research route priority: jina -> serpapi -> local"
        )
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
