package atropos.core.provider

import atropos.core.paid.EmergencyPaidGate

enum class AtroposCostPolicy { FREE_ONLY, FREE_AND_CREDIT, LOCAL_ONLY, PAID_EMERGENCY_UNLOCKED }

data class ProviderEligibility(val provider: ProviderDescriptor, val quota: ProviderQuotaRecord?, val eligible: Boolean, val reason: String)

data class RoutePolicyDecision(
    val task: ProviderTask,
    val selectedProviderId: String?,
    val selected: ProviderDescriptor?,
    val eligible: List<ProviderEligibility>,
    val skipped: List<ProviderEligibility>,
    val degraded: Boolean = false,
    val queued: Boolean = false,
    val queueReason: String? = null
) {
    fun explain(): String {
        val state = when {
            queued -> "queued"
            degraded -> "degraded"
            selectedProviderId != null -> "selected"
            else -> "unresolved"
        }
        return "task=${task.kind.name.lowercase()} state=$state " +
            "selected=${selectedProviderId ?: "none"} skipped=" +
            skipped.joinToString("; ") { "${it.provider.id}:${it.reason}" } +
            (queueReason?.let { " queue_reason=$it" } ?: "")
    }
}

class FreeModeGuard(private val policy: AtroposCostPolicy = AtroposCostPolicy.FREE_ONLY) {
    fun allows(descriptor: ProviderDescriptor): Boolean =
        when (policy) {
            AtroposCostPolicy.LOCAL_ONLY -> descriptor.costMode == CostMode.LOCAL
            AtroposCostPolicy.FREE_ONLY -> descriptor.costMode in setOf(CostMode.LOCAL, CostMode.FREE, CostMode.COOLDOWN_OK, CostMode.OPTIONAL_FREE)
            AtroposCostPolicy.FREE_AND_CREDIT -> descriptor.costMode in setOf(CostMode.LOCAL, CostMode.FREE, CostMode.COOLDOWN_OK, CostMode.CREDIT_POOL, CostMode.OPTIONAL_FREE)
            AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED -> true
        }
}

class ProviderEligibilityFilter(
    private val guard: FreeModeGuard,
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    fun evaluate(descriptor: ProviderDescriptor, quota: ProviderQuotaRecord?): ProviderEligibility {
        if (!guard.allows(descriptor)) return ProviderEligibility(descriptor, quota, false, "blocked_by_cost_policy")
        if (!descriptor.isLocal && descriptor.requiredEnv.isEmpty()) return ProviderEligibility(descriptor, quota, false, "missing_secret_contract")
        if (descriptor.isLocal && quota == null) return ProviderEligibility(descriptor, quota, true, "local_ready")
        if (quota == null) return ProviderEligibility(descriptor, quota, false, "missing_quota_record")
        if (!descriptor.isLocal && !quota.configured) return ProviderEligibility(descriptor, quota, false, "not_configured")
        if (!descriptor.isLocal && !quota.verified) return ProviderEligibility(descriptor, quota, false, "not_verified")
        val isUnlocked = paidGate.isProviderUnlocked(descriptor.id)
        if (quota.paidLocked && descriptor.isPaidLocked() && !isUnlocked) return ProviderEligibility(descriptor, quota, false, "paid_locked")
        if (!quota.availableAt(nowEpochMs())) return ProviderEligibility(descriptor, quota, false, quota.state.name.lowercase())
        return ProviderEligibility(descriptor, quota, true, "eligible")
    }
}

class FallbackResolver(private val registry: ProviderDescriptorRegistry) {
    fun chainFor(providerId: String): List<ProviderDescriptor> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<ProviderDescriptor>()
        fun visit(id: String) {
            if (!seen.add(id)) return
            val d = registry.getById(id) ?: return
            out += d
            d.fallbackChain.forEach(::visit)
        }
        visit(providerId)
        return out
    }
    fun providersForCapability(capability: ApiCapability) = registry.getByCapability(capability)
}

class RoutePolicy(
    private val registry: ProviderDescriptorRegistry,
    private val ledger: QuotaLedger,
    private val costPolicy: AtroposCostPolicy = AtroposCostPolicy.FREE_ONLY,
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    private val filter = ProviderEligibilityFilter(FreeModeGuard(costPolicy), paidGate, nowEpochMs)

    fun decide(task: ProviderTask): RoutePolicyDecision {
        val candidates = registry.getByCapability(task.capability).ifEmpty { registry.getByCapability(ApiCapability.CHAT) }
        val evaluated = candidates.map { filter.evaluate(it, ledger.get(it.id)) }
        val eligible = evaluated.filter { it.eligible }.sortedWith(
            compareBy<ProviderEligibility>(
                {
                    if (!task.localFirst && it.provider.isLocal) 1 else 0
                },
                {
                    when {
                        costPolicy == AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED &&
                            it.provider.isPaidLocked() &&
                            paidGate.isProviderUnlocked(it.provider.id) -> 0
                        it.provider.isPaidLocked() -> 2
                        costPolicy == AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED &&
                            it.provider.costMode != CostMode.LOCAL -> 0
                        costPolicy == AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED -> 1
                        else -> 1
                    }
                },
                { taskPriority(task, it.provider) },
                { it.provider.quotaTier },
                { it.quota?.successScore?.let { score -> -score } ?: 0.0 },
                { it.quota?.latencyMsAvg ?: Long.MAX_VALUE },
                { it.provider.id }
            )
        )
        val selected = eligible.firstOrNull()?.provider
        return if (selected != null) {
            RoutePolicyDecision(task, selected.id, selected, eligible, evaluated.filterNot { it.eligible })
        } else {
            RoutePolicyDecision(
                task = task,
                selectedProviderId = null,
                selected = null,
                eligible = emptyList(),
                skipped = evaluated,
                degraded = true,
                queued = true,
                queueReason = "no eligible provider"
            )
        }
    }

    private fun taskPriority(task: ProviderTask, descriptor: ProviderDescriptor): Int {
        val capabilityPenalty = if (descriptor.hasCapability(task.capability)) 0 else 20
        val localityPenalty = if (task.localFirst && descriptor.isLocal) 0 else 1
        return capabilityPenalty + localityPenalty + descriptor.quotaTier
    }
}
