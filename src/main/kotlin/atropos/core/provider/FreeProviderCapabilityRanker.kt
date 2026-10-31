/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.dopamine.ProviderDopamineRewardTracker
import java.util.Locale

/**
 * Deterministic best-first free-tier ordering for the provider cascade.
 *
 * Evaluates task requirements and sorts available providers deterministically based on:
 * 1. Required Context Window (models smaller than payloadTokens are filtered out immediately).
 * 2. Capabilities (ApiCapability.CODE, ApiCapability.REPAIR).
 * 3. Historical Dopamine Score (Reliability).
 *
 * If deepseek or openrouter is the best deterministic fit for a massive repair, it is routed first.
 */
object FreeProviderCapabilityRanker {

    fun capabilityScore(descriptor: ProviderDescriptor): Int {
        var score = 0
        if (descriptor.hasCapability(ApiCapability.CHAT)) score += 1
        if (descriptor.hasCapability(ApiCapability.CODE)) score += 3
        if (descriptor.hasCapability(ApiCapability.REPAIR)) score += 5
        if (descriptor.hasCapability(ApiCapability.PLAN)) score += 1
        if (descriptor.hasCapability(ApiCapability.LARGE_CONTEXT)) score += 2
        if (descriptor.hasCapability(ApiCapability.VISION)) score += 1
        return score
    }

    fun isFree(descriptor: ProviderDescriptor): Boolean =
        descriptor.costMode in setOf(CostMode.FREE, CostMode.COOLDOWN_OK, CostMode.OPTIONAL_FREE)

    fun rankFreeFirst(
        candidates: List<String>,
        estimatedTokens: Int,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
        dopamineTracker: ProviderDopamineRewardTracker = ProviderDopamineRewardTracker.SHARED
    ): List<String> {
        val cleaned = candidates.map { it.trim().lowercase(Locale.US) }.distinct()

        // 1. Context Window Filtering: Filter out models smaller than required payload tokens immediately
        val fits = if (estimatedTokens > 0) {
            cleaned.filter { ProviderContextWindow.fits(it, estimatedTokens, registry) }
        } else emptyList()
        val eligible = if (fits.isNotEmpty()) fits else cleaned

        val free = eligible.filter { registry.getById(it)?.let { d -> isFree(d) } == true }
        val nonFree = eligible.filter { !free.contains(it) }

        // 2. Deterministic ranking: Dopamine reliability -> Capabilities (CODE, REPAIR) -> Context capacity
        val rankedFree = free.sortedWith(
            compareByDescending<String> { dopamineTracker.getNetRewardScore(it) }
                .thenByDescending { descId ->
                    val desc = registry.getById(descId)
                    if (desc != null) capabilityScore(desc) else 0
                }
                .thenByDescending { ProviderContextWindow.of(it, registry) }
                .thenBy { registry.getById(it)?.quotaTier ?: Int.MAX_VALUE }
                .thenBy { cleaned.indexOf(it) }
        )

        val rankedNonFree = nonFree.sortedWith(
            compareByDescending<String> { dopamineTracker.getNetRewardScore(it) }
                .thenByDescending { descId ->
                    val desc = registry.getById(descId)
                    if (desc != null) capabilityScore(desc) else 0
                }
                .thenByDescending { ProviderContextWindow.of(it, registry) }
                .thenBy { cleaned.indexOf(it) }
        )

        return rankedFree + rankedNonFree
    }
}
