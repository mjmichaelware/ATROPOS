package atropos.core.provider

import atropos.core.AtroposConfig
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
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val healthyProviderIds: (() -> Set<String>)? = null,
    private val preferredProviderIds: (() -> List<String>)? = null,
    private val localOnly: Boolean = AtroposConfig.load().runtime.localOnly
) {
    private val filter = ProviderEligibilityFilter(FreeModeGuard(costPolicy), paidGate, nowEpochMs)

    fun decide(task: ProviderTask): RoutePolicyDecision {
        val candidates = registry.getByCapability(task.capability).ifEmpty { registry.getByCapability(ApiCapability.CHAT) }
        val evaluated = candidates.map { candidate ->
            val base = filter.evaluate(candidate, ledger.get(candidate.id))
            if (localOnly && !candidate.isLocal) {
                base.copy(eligible = false, reason = "blocked_by_local_only")
            } else if (healthyProviderIds != null && candidate.id !in healthyProviderIds.invoke()) {
                base.copy(eligible = false, reason = "not_in_healthy_set")
            } else base
        }
        // [ProviderPreferenceOrder] owns the ordering. It is Source Doc 2
        // §.300 §7's six terms, already written as a lexicographic comparator
        // for exactly the reason this route needs — a later term may only break
        // a tie in an earlier one, so a fast paid provider can never beat a
        // slow free one. Restating those six here was a second ordering of the
        // same thing, and the two only have to disagree once for the free-first
        // guarantee to stop holding.
        //
        // What this route knows that the document's terms do not is the cost
        // policy in force: whether the operator has emergency-unlocked a paid
        // provider, and whether this task asked for local first. Those outrank
        // all six, so they go in as a tier ahead of them rather than as terms
        // among them.
        val eligibilityOrder = EligibilityAlgorithm.rank(
            evaluated.map { candidate ->
                ProviderHealth(
                    providerId = candidate.provider.id,
                    state = candidate.quota?.state ?: ProviderAvailabilityState.READY,
                    verified = candidate.quota?.verified ?: candidate.provider.isLocal,
                    activeModel = candidate.provider.endpointId ?: candidate.provider.id,
                    latencyMsAvg = candidate.quota?.latencyMsAvg,
                    successScore = candidate.quota?.successScore ?: 0.0
                )
            }
        ).mapIndexed { index, score -> score.providerId to index }.toMap()

        val eligible = ProviderPreferenceOrder.order(
            eligible = evaluated.filter { it.eligible },
            taskPriority = { providerId ->
                evaluated.firstOrNull { it.provider.id == providerId }
                    ?.let { taskPriority(task, it.provider) }
                    ?: Int.MAX_VALUE
            },
            tier = { candidate -> localTier(task, candidate) * COST_TIERS + costTier(candidate) },
            finalTieBreak = { providerId ->
                preferredProviderIds?.invoke()?.indexOf(providerId)?.takeIf { it >= 0 }?.minus(1)
                    ?: eligibilityOrder[providerId] ?: Int.MAX_VALUE
            }
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

    /**
     * Whether a local provider must wait behind the remote ones.
     *
     * `localFirst` is the task saying it wants the on-device toolchain tried
     * first. When it does not, local sorts last — not because local is worse,
     * but because a task that asked for a remote capability is not served by
     * the thing that cannot provide it.
     */
    private fun localTier(task: ProviderTask, candidate: ProviderEligibility): Int =
        if (!task.localFirst && candidate.provider.isLocal) 1 else 0

    /**
     * Where the cost policy places this provider, lowest first.
     *
     * Combined with [localTier] by multiplying by [COST_TIERS], which is a
     * lexicographic encoding rather than a sum: cost values are strictly below
     * the radix, so no cost tier can ever carry a candidate past a better local
     * tier.
     */
    private fun costTier(candidate: ProviderEligibility): Int = when {
        costPolicy == AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED &&
            candidate.provider.isPaidLocked() &&
            paidGate.isProviderUnlocked(candidate.provider.id) -> 0
        candidate.provider.isPaidLocked() -> 2
        costPolicy == AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED &&
            candidate.provider.costMode != CostMode.LOCAL -> 0
        else -> 1
    }

    private fun taskPriority(task: ProviderTask, descriptor: ProviderDescriptor): Int {
        val capabilityPenalty = if (descriptor.hasCapability(task.capability)) 0 else 20
        val localityPenalty = if (task.localFirst && descriptor.isLocal) 0 else 1
        return capabilityPenalty + localityPenalty + descriptor.quotaTier
    }

    private companion object {
        /** One more than the largest value [costTier] returns. */
        const val COST_TIERS = 3
    }
}
