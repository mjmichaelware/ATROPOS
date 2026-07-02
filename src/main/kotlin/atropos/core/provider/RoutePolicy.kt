package atropos.core.provider

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
    fun explain(): String =
        "task=${task.kind.name.lowercase()} selected=${selectedProviderId ?: "none"} skipped=" +
            skipped.joinToString("; ") { "${it.provider.id}:${it.reason}" }
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

class ProviderEligibilityFilter(private val guard: FreeModeGuard, private val nowEpochMs: () -> Long = { System.currentTimeMillis() }) {
    fun evaluate(descriptor: ProviderDescriptor, quota: ProviderQuotaRecord?): ProviderEligibility {
        if (!guard.allows(descriptor)) return ProviderEligibility(descriptor, quota, false, "blocked_by_cost_policy")
        if (!descriptor.isLocal && descriptor.requiredEnv.isEmpty()) return ProviderEligibility(descriptor, quota, false, "missing_secret_contract")
        if (quota == null) return ProviderEligibility(descriptor, quota, true, "quota_unknown")
        if (quota.paidLocked && descriptor.isPaidLocked()) return ProviderEligibility(descriptor, quota, false, "paid_locked")
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
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    private val filter = ProviderEligibilityFilter(FreeModeGuard(costPolicy), nowEpochMs)

    fun decide(task: ProviderTask): RoutePolicyDecision {
        val candidates = registry.getByCapability(task.capability).ifEmpty { registry.getByCapability(ApiCapability.CHAT) }
        val evaluated = candidates.map { filter.evaluate(it, ledger.get(it.id)) }
        val eligible = evaluated.filter { it.eligible }.sortedWith(
            compareBy<ProviderEligibility>(
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
            RoutePolicyDecision(task, null, null, emptyList(), evaluated, degraded = task.localFirst, queued = true, queueReason = "no eligible provider")
        }
    }

    private fun taskPriority(task: ProviderTask, descriptor: ProviderDescriptor): Int =
        when (task.kind) {
            ProviderTaskKind.CHAT_PROMPT -> when (descriptor.id) {
                "groq" -> 1; "gemini" -> 2; "openrouter" -> 3; "github_models" -> 4; "cloudflare_ai" -> 5; "ollama" -> 8; "local" -> 9; else -> 20
            }
            ProviderTaskKind.FAST_CODE_DRAFT, ProviderTaskKind.COMPILE_REPAIR -> when (descriptor.id) {
                "groq" -> 1; "openrouter" -> 2; "github_models" -> 3; "gemini" -> 4; "ollama" -> 8; "local" -> 9; else -> 20
            }
            ProviderTaskKind.ARCHITECTURE_PLAN, ProviderTaskKind.LARGE_SOURCE_DOCS -> when (descriptor.id) {
                "gemini" -> 1; "groq" -> 2; "github_models" -> 3; "openrouter" -> 4; "ollama" -> 8; "local" -> 9; else -> 20
            }
            ProviderTaskKind.WEB_DOCS_LOOKUP -> when (descriptor.id) {
                "jina" -> 1; "gemini" -> 2; "serpapi" -> 6; "local" -> 9; else -> 20
            }
            ProviderTaskKind.ASSET_GENERATION, ProviderTaskKind.SCREENSHOT_REVIEW -> when (descriptor.id) {
                "huggingface" -> 1; "fal" -> 3; "replicate" -> 4; "local" -> 9; else -> 20
            }
            else -> if (descriptor.id == "local") 1 else 10
        }
}
