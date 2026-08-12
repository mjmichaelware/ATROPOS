/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Orders cascade candidates local-first, then free-first.
 *
 * The order used to be a hand-written list with `ollama` at the **end**, which
 * is local-*last* — the inverse of the policy — and the patch order named no
 * local provider at all. Ordering is where "local-first, free-first" actually
 * lives: once the cascade starts walking a list, whichever provider sits first
 * is the one that gets the work.
 *
 * Cost classification is read from the existing [ProviderDescriptorRegistry].
 * Nothing here re-declares what a provider costs.
 */
object ProviderCascadeOrder {

    /**
     * Rank by cost. Lower runs first.
     *
     * A provider the registry does not know is ranked **last** rather than
     * assumed free: an unknown cost is not a free cost. [CostMode.PAID_LOCKED]
     * has no rank because it is removed entirely.
     */
    private fun rank(costMode: CostMode?): Int = when (costMode) {
        CostMode.LOCAL -> 0
        CostMode.FREE -> 1
        CostMode.OPTIONAL_FREE -> 2
        CostMode.COOLDOWN_OK -> 3
        CostMode.CREDIT_POOL -> 4
        CostMode.PAID_LOCKED -> Int.MAX_VALUE
        null -> Int.MAX_VALUE - 1
    }

    /**
     * @param candidates provider ids the caller has confirmed are configured.
     * @return the same ids, local-first then free-first, with paid-locked
     *   providers removed. Order within a tier follows the caller's order, so a
     *   deliberate preference between two free providers is preserved.
     */
    fun order(
        candidates: List<String>,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
    ): List<String> {
        val descriptors = registry.getAll().associateBy { it.id }
        return candidates
            .distinct()
            // A paid provider must never enter the cascade. The policy engine
            // would refuse the call anyway; leaving it in the order would mean
            // the cascade spends attempts discovering that.
            .filterNot { descriptors[it]?.costMode == CostMode.PAID_LOCKED }
            .withIndex()
            .sortedWith(compareBy({ rank(descriptors[it.value]?.costMode) }, { it.index }))
            .map { it.value }
    }

    /** True when [providerId] runs on this machine and costs nothing to call. */
    fun isLocal(
        providerId: String,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
    ): Boolean = registry.getById(providerId)?.costMode == CostMode.LOCAL
}
