/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.paid.EmergencyPaidGate

/**
 * Orders cascade candidates free-first, then cooldown, then credit pool.
 *
 * The order used to be a hand-written list with `ollama` at the **end**, which
 * was free-*last* - the inverse of the policy - and the patch order named no
 * free provider at all. Ordering is where "free-first" actually
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
        CostMode.FREE -> 0
        CostMode.COOLDOWN_OK -> 1
        CostMode.OPTIONAL_FREE -> 2
        CostMode.CREDIT_POOL -> 3
        CostMode.PAID_LOCKED -> Int.MAX_VALUE
        null -> Int.MAX_VALUE - 1
    }

    /**
     * @param candidates provider ids the caller has confirmed are configured.
     * @return the same ids, free-first, with paid-locked
     *   providers removed. Order within a tier follows the caller's order, so a
     *   deliberate preference between two free providers is preserved.
     */
    fun order(
        candidates: List<String>,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
        allowUnlockedPaid: Boolean = false,
        paidGate: EmergencyPaidGate = EmergencyPaidGate()
    ): List<String> {
        val descriptors = registry.getAll().associateBy { it.id }
        return candidates
            .distinct()
            // A paid provider must never enter the cascade. The policy engine
            // would refuse the call anyway; leaving it in the order would mean
            // the cascade spends attempts discovering that.
            .filterNot {
                descriptors[it]?.billingClass() == BillingClass.PAID &&
                    !(allowUnlockedPaid && paidGate.isProviderUnlocked(it))
            }
            .withIndex()
            .sortedWith(compareBy({ rank(descriptors[it.value]?.costMode) }, { it.index }))
            .map { it.value }
    }

    /** True when [providerId] is free or cooldown-ok tier. */
    fun isFreeTier(
        providerId: String,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
    ): Boolean = registry.getById(providerId)?.costMode in setOf(CostMode.FREE, CostMode.COOLDOWN_OK)

    /** True when [providerId] is a local provider (runs locally, e.g., ollama). */
    fun isLocal(
        providerId: String,
        registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
    ): Boolean = registry.getById(providerId)?.id == "ollama"
}
