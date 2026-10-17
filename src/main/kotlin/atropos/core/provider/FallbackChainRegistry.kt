/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

data class ProviderSpec(
    val id: Int,
    val name: String,
    val quotaWeight: Int,
    val costPerToken: Double,
    val isFree: Boolean = false
)

data class TaskRoute(
    val task: String,
    val preferredProviderId: Int,
    val fallbackProviderId: Int,
    val alternativeProviderId: Int,
    val rateLimitHits: Int = 0
)

/**
 * Universally error-aware fallback chain registry and context-aware provider router.
 *
 * Implements universal error parsing across raw network exceptions and HTTP JSON error payloads,
 * thread-safe API key array exhaustion across configuration arrays, and deterministic routing.
 */
object FallbackChainRegistry {
    const val LOCAL_TOOLCHAIN = 0
    const val CUSTOM_USER_API = 29

    val HIGH_CONTEXT_FRONTIER_MODELS = listOf("gemini", "gemini-2.5-pro", "anthropic", "openrouter", "OpenRouter-Claude")
    val LOW_CONTEXT_MODELS = setOf("groq", "cerebras", "sambanova", "ollama_small")

    val providers = listOf(
        ProviderSpec(LOCAL_TOOLCHAIN, "LocalToolchain", 0, 0.0, true),
        ProviderSpec(1, "OpenRouter-Claude", 10, 0.000015),
        ProviderSpec(2, "OpenRouter-GPT4", 12, 0.000030),
        ProviderSpec(3, "OpenRouter-FreeRotation", 1, 0.0, true),
        ProviderSpec(CUSTOM_USER_API, "CustomUserApi", 5, 0.000005)
    )

    // Task routing matrix as data (14 tasks x 6 columns: task, pref, fb, alt, weight, cost)
    val taskRoutingMatrix = listOf(
        TaskRoute("chat", 1, 2, 3),
        TaskRoute("code", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("repair", 1, LOCAL_TOOLCHAIN, 3),
        TaskRoute("planning", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("docs", LOCAL_TOOLCHAIN, 1, 3),
        TaskRoute("search", LOCAL_TOOLCHAIN, 1, 3),
        TaskRoute("embed", LOCAL_TOOLCHAIN, 1, 2),
        TaskRoute("memory", LOCAL_TOOLCHAIN, 1, CUSTOM_USER_API),
        TaskRoute("secret", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1),
        TaskRoute("edge", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 3),
        TaskRoute("asset", LOCAL_TOOLCHAIN, 1, 2),
        TaskRoute("completion", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("audit", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1),
        TaskRoute("deploy", LOCAL_TOOLCHAIN, 1, 2)
    )

    // Named, inspectable chains
    val CHAT_CHAIN = listOf(1, 2, 3)
    val CODE_CHAIN = listOf(1, 2, LOCAL_TOOLCHAIN)
    val REPAIR_CHAIN = listOf(1, LOCAL_TOOLCHAIN, 3)
    val PLANNING_CHAIN = listOf(1, 2, LOCAL_TOOLCHAIN)
    val DOCS_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 3)
    val SEARCH_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 3)
    val EMBED_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 2)
    val MEMORY_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, CUSTOM_USER_API)
    val SECRET_CHAIN = listOf(LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1)
    val EDGE_CHAIN = listOf(LOCAL_TOOLCHAIN, CUSTOM_USER_API, 3)
    val ASSET_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 2)

    /** OpenRouter free-model rotation when rate-limited. */
    fun getActiveOpenRouterModel(rateLimited: Boolean): Int {
        return if (rateLimited) 3 else 1
    }

    /** Primary sort logic: quota_weight ASC. */
    fun getSortedProviders(): List<ProviderSpec> {
        return providers.sortedBy { it.quotaWeight }
    }

    /** Compatibility lookup delegated to the canonical typed chain owner. */
    fun canonicalChain(capability: ApiCapability): FallbackChain? =
        FallbackChain.forCapability(capability)

    // --- Key Management Delegations ---

    fun registerKeyArray(providerId: String, keys: List<String>) =
        ProviderKeyRegistry.registerKeyArray(providerId, keys)

    fun loadKeysFromJson(jsonString: String) =
        ProviderKeyRegistry.loadKeysFromJson(jsonString)

    fun loadFromEnvironmentAndVault() =
        ProviderKeyRegistry.loadFromEnvironmentAndVault()

    fun getActiveKey(providerId: String): String? =
        ProviderKeyRegistry.getActiveKey(providerId)

    fun markKeyDead(providerId: String, key: String) =
        ProviderKeyRegistry.markKeyDead(providerId, key)

    fun rotateKey(providerId: String): String? =
        ProviderKeyRegistry.rotateKey(providerId)

    fun hasMoreKeys(providerId: String): Boolean =
        ProviderKeyRegistry.hasMoreKeys(providerId)

    fun areKeysExhausted(providerId: String): Boolean =
        ProviderKeyRegistry.areKeysExhausted(providerId)

    fun isPipelineExhausted(providers: List<String>): Boolean =
        ProviderKeyRegistry.isPipelineExhausted(providers)

    fun getAllActiveKeys(): Map<String, String> =
        ProviderKeyRegistry.getAllActiveKeys()

    fun resetState() =
        ProviderKeyRegistry.resetState()

    // --- Universal Error Parsing Delegations ---

    fun parseError(throwable: Throwable): ParsedUniversalError =
        UniversalErrorParser.parse(throwable)

    fun parseError(raw: String, httpCode: Int? = null): ParsedUniversalError =
        UniversalErrorParser.parse(raw, httpCode)

    fun parseAnyError(error: Any?): ParsedUniversalError =
        UniversalErrorParser.parseAny(error)

    // --- Deterministic Context-Aware Routing ---

    /**
     * Categorize universal error and deterministically route:
     * - Context/Token bounds exceeded: instantly route to known high-context frontier model.
     * - Rate limits / overload: shift to next key in configuration array, or next parallel provider.
     * - Dead key (401/403): mark key as dead and instantly rotate to next key.
     * - Network timeouts/drops: execute standard reliable fallback.
     * - Total exhaustion: exhaust every configured key across arrays before declaring pipeline failure.
     */
    fun routeNext(
        currentProvider: String,
        error: ParsedUniversalError,
        chain: List<String> = emptyList()
    ): RoutingDecision {
        val cleanCurrent = currentProvider.trim().lowercase()

        return when (error.category) {
            UniversalErrorCategory.CONTEXT_EXCEEDED -> {
                val frontier = HIGH_CONTEXT_FRONTIER_MODELS.firstOrNull { it.lowercase() != cleanCurrent } ?: "gemini"
                RoutingDecision(
                    action = RoutingAction.ROUTE_HIGH_CONTEXT,
                    targetProvider = frontier,
                    reason = "Context/token bounds exceeded (${error.message}); routing instantly to high-context frontier model: $frontier"
                )
            }

            UniversalErrorCategory.RATE_LIMIT_OVERLOAD -> {
                if (hasMoreKeys(cleanCurrent)) {
                    val nextKey = rotateKey(cleanCurrent)
                    RoutingDecision(
                        action = RoutingAction.ROTATE_KEY,
                        targetProvider = cleanCurrent,
                        targetKey = nextKey,
                        reason = "Rate limit / overload (HTTP ${error.httpCode ?: 429}) on $cleanCurrent; shifted to next available API key in configuration array"
                    )
                } else {
                    val nextProvider = getNextParallelProvider(cleanCurrent, chain)
                    if (nextProvider != null) {
                        RoutingDecision(
                            action = RoutingAction.NEXT_PARALLEL_PROVIDER,
                            targetProvider = nextProvider,
                            reason = "All keys for $cleanCurrent exhausted under rate limit; shifted to next parallel provider: $nextProvider"
                        )
                    } else {
                        RoutingDecision(
                            action = RoutingAction.PIPELINE_EXHAUSTED,
                            reason = "Total pipeline failure: every configured API key across loaded arrays exhausted under rate limits"
                        )
                    }
                }
            }

            UniversalErrorCategory.AUTH_DEAD_KEY -> {
                val deadKey = getActiveKey(cleanCurrent)
                if (deadKey != null) {
                    markKeyDead(cleanCurrent, deadKey)
                }
                val nextKey = rotateKey(cleanCurrent) ?: getActiveKey(cleanCurrent)
                if (nextKey != null) {
                    RoutingDecision(
                        action = RoutingAction.ROTATE_KEY,
                        targetProvider = cleanCurrent,
                        targetKey = nextKey,
                        reason = "Authentication/dead key (HTTP ${error.httpCode ?: 401}) on $cleanCurrent; marked dead and rotated to next key"
                    )
                } else {
                    val nextProvider = getNextParallelProvider(cleanCurrent, chain)
                    if (nextProvider != null) {
                        RoutingDecision(
                            action = RoutingAction.NEXT_PARALLEL_PROVIDER,
                            targetProvider = nextProvider,
                            reason = "All keys for $cleanCurrent are dead; rotated to next provider: $nextProvider"
                        )
                    } else {
                        RoutingDecision(
                            action = RoutingAction.PIPELINE_EXHAUSTED,
                            reason = "Total pipeline failure: all configured API keys for all providers are dead or invalid"
                        )
                    }
                }
            }

            UniversalErrorCategory.NETWORK_TIMEOUT_DROP -> {
                val nextProvider = getNextFallbackProvider(cleanCurrent, chain)
                if (nextProvider != null) {
                    RoutingDecision(
                        action = RoutingAction.RELIABLE_FALLBACK,
                        targetProvider = nextProvider,
                        reason = "Network timeout/drop on $cleanCurrent (${error.message}); executing reliable fallback to $nextProvider"
                    )
                } else {
                    RoutingDecision(
                        action = RoutingAction.PIPELINE_EXHAUSTED,
                        reason = "Total pipeline failure: network drop on $cleanCurrent with no remaining fallback providers"
                    )
                }
            }

            UniversalErrorCategory.UNKNOWN -> {
                val nextProvider = getNextFallbackProvider(cleanCurrent, chain)
                if (nextProvider != null) {
                    RoutingDecision(
                        action = RoutingAction.RELIABLE_FALLBACK,
                        targetProvider = nextProvider,
                        reason = "Unclassified error on $cleanCurrent (${error.message}); advancing to next provider: $nextProvider"
                    )
                } else {
                    RoutingDecision(
                        action = RoutingAction.PIPELINE_EXHAUSTED,
                        reason = "Total pipeline failure: all providers and keys exhausted"
                    )
                }
            }
        }
    }

    private fun getNextParallelProvider(current: String, chain: List<String>): String? {
        val candidates = if (chain.isNotEmpty()) chain else FallbackChain.CHAT.links.filterNot { it in ChainLink.TERMINAL }
        val currentIdx = candidates.indexOfFirst { it.equals(current, ignoreCase = true) }
        val remaining = if (currentIdx >= 0) candidates.drop(currentIdx + 1) else candidates
        return remaining.firstOrNull { it.lowercase() != current && !areKeysExhausted(it) }
            ?: remaining.firstOrNull { it.lowercase() != current }
    }

    private fun getNextFallbackProvider(current: String, chain: List<String>): String? {
        val candidates = if (chain.isNotEmpty()) chain else FallbackChain.CHAT.links.filterNot { it in ChainLink.TERMINAL }
        val currentIdx = candidates.indexOfFirst { it.equals(current, ignoreCase = true) }
        return if (currentIdx >= 0 && currentIdx + 1 < candidates.size) {
            candidates[currentIdx + 1]
        } else {
            candidates.firstOrNull { it.lowercase() != current }
        }
    }
}
