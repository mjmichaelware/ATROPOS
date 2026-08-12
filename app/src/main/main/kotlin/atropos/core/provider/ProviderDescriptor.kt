package atropos.core.provider

enum class CostMode { LOCAL, FREE, COOLDOWN_OK, CREDIT_POOL, OPTIONAL_FREE, PAID_LOCKED }

enum class ApiCapability {
    CHAT, CODE, REPAIR, PLAN, LARGE_CONTEXT, VISION, EMBED, ASSET, WEB, READER,
    VECTOR_DB, DATABASE, STORAGE, EDGE, SECRET, CI, LOCAL_TOOL
}

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val costMode: CostMode,
    val quotaTier: Int,
    val capabilities: Set<ApiCapability>,
    val requiredEnv: List<String> = emptyList(),
    val fallbackChain: List<String> = emptyList(),
    val endpointId: String? = null,
    val isLocal: Boolean = false,
    val notes: String = ""
) {
    fun isFreeEligible(): Boolean =
        costMode in setOf(CostMode.LOCAL, CostMode.FREE, CostMode.COOLDOWN_OK, CostMode.CREDIT_POOL, CostMode.OPTIONAL_FREE)

    fun isPaidLocked(): Boolean = costMode == CostMode.PAID_LOCKED
    fun hasCapability(capability: ApiCapability): Boolean = capability in capabilities
}
