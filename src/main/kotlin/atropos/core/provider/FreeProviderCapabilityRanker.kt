/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.util.Locale

/**
 * Deterministic best-first free-tier ordering for the provider cascade.
 *
 * Ranks active free providers in descending order of context window size,
 * breaking ties with capability breadth, then stability tier, then input
 * order. Providers that cannot hold the given payload are demoted last, so
 * the mathematically best *capable* free provider is always tried first.
 * Paid providers are never promoted by this ranking — callers must filter
 * them out before calling (see [ProviderCascadeOrder.order]).
 */
object FreeProviderCapabilityRanker {

    fun capabilityScore(descriptor: ProviderDescriptor): Int {
        var score = 0
        if (descriptor.hasCapability(ApiCapability.CHAT)) score += 1
        if (descriptor.hasCapability(ApiCapability.CODE)) score += 1
        if (descriptor.hasCapability(ApiCapability.REPAIR)) score += 1
        if (descriptor.hasCapability(ApiCapability.PLAN)) score += 1
        if (descriptor.hasCapability(ApiCapability.LARGE_CONTEXT)) score += 1
        if (descriptor.hasCapability(ApiCapability.VISION)) score += 1
        return score
    }

    fun isFree(descriptor: ProviderDescriptor): Boolean =
        descriptor.costMode in setOf(CostMode.FREE, CostMode.COOLDOWN_OK, CostMode.OPTIONAL_FREE)

    fun rankFreeFirst(
        candidates: List<String>,
        estimatedTokens: Int,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
    ): List<String> {
        val cleaned = candidates.map { it.trim().lowercase(Locale.US) }.distinct()
        val free = cleaned.filter { registry.getById(it)?.let { isFree(it) } == true }
        val nonFree = cleaned.filter { !free.contains(it) }
        return free.sortedWith(
            compareBy<String> { !fits(it, estimatedTokens, registry) } // fits-first
                .thenByDescending { ProviderContextWindow.of(it, registry) } // context window desc
                .thenByDescending { capabilityScore(registry.getById(it)!!) }
                .thenBy { registry.getById(it)?.quotaTier ?: Int.MAX_VALUE }
                .thenBy { cleaned.indexOf(it) }
        ) + nonFree.sortedBy { cleaned.indexOf(it) }
    }
}
