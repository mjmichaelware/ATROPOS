/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Source Doc 2 §.300 §7's six sort terms, in the document's order.
 *
 * > Sort eligible providers by: quota_weight ASC · task_priority ASC ·
 * > remaining_estimate DESC · recent_success_score DESC · latency_estimate ASC ·
 * > cooldown_risk ASC
 *
 * [ProviderEligibilityFilter] already answers *which* providers may serve. This
 * answers *in what order*, which was the missing half — and the half that
 * carries the free-first guarantee.
 *
 * `quota_weight ASC` first is that guarantee expressed as an ordering rather
 * than as a separate rule. Weight 0 is the local toolchain, 1 a preferred free
 * provider, 2 optional free or credit, 3 scarce, 9 paid emergency. Sorting
 * ascending puts local before free before credit before paid without anything
 * having to say so, and — critically — a provider cannot be preferred over a
 * cheaper one by scoring well on latency or success rate. Cost dominates.
 *
 * A comparator rather than a weighted score, for exactly that reason. Any
 * scoring function lets a later term outweigh an earlier one given a large
 * enough difference, which is how a fast paid provider quietly beats a slow free
 * one. Lexicographic comparison makes that impossible: a later term can only
 * break a tie in an earlier one.
 */
object ProviderPreferenceOrder {

    /**
     * Orders eligible providers by the six terms.
     *
     * @param taskPriority position in the task's chain, from [FallbackChain] or
     *   [RoutedTask]. Absent means "not in this task's chain", which sorts last
     *   rather than first — a provider the chain does not name should never be
     *   preferred over one it does.
     */
    fun order(
        eligible: List<ProviderEligibility>,
        taskPriority: (String) -> Int = { Int.MAX_VALUE },
        /**
         * A precedence tier applied *before* the document's six terms.
         *
         * The six describe how to choose among providers that are equally
         * permitted. A caller may know something that outranks all of them —
         * the cost policy has emergency-unlocked a paid provider, or the task
         * did not ask for local-first — and those are tiers, not terms: no
         * amount of success rate may promote a paid-locked provider past an
         * unlocked one. Sorting them ahead is the only shape that says so.
         *
         * Zero for every candidate by default, which leaves the document's
         * ordering exactly as it stands.
         */
        tier: (ProviderEligibility) -> Int = { 0 },
        /**
         * A last tie-break after all six terms, before falling back to id.
         *
         * Alphabetical order is a deterministic answer, not a good one. A
         * caller with a live health ranking has something better to say about
         * two providers the six terms could not separate.
         */
        finalTieBreak: (String) -> Int = { 0 }
    ): List<ProviderEligibility> =
        eligible.sortedWith(
            compareBy<ProviderEligibility> { tier(it) }
                .thenBy { weightOf(it) }
                .thenBy { taskPriority(it.provider.id) }
                .thenByDescending { remainingEstimate(it) }
                .thenByDescending { it.quota?.successScore ?: 0.0 }
                .thenBy { it.quota?.latencyMsAvg ?: Long.MAX_VALUE }
                .thenBy { cooldownRisk(it) }
                .thenBy { finalTieBreak(it.provider.id) }
                .thenBy { it.provider.id }
        )

    /**
     * Orders against a named chain, using its positions as task priority.
     *
     * The common case: a capability implies a chain, and the chain states the
     * intended order. This keeps the document's ordering authoritative while
     * still letting live quota and health break ties within a tier.
     */
    fun orderForChain(eligible: List<ProviderEligibility>, chain: FallbackChain): List<ProviderEligibility> =
        order(eligible) { providerId ->
            chain.positionOf(providerId).let { if (it < 0) Int.MAX_VALUE else it }
        }

    /**
     * Quota weight, preferring the live record over the descriptor.
     *
     * The ledger's weight reflects what a provider has actually been observed
     * to cost; the descriptor's is the declared default. Preferring the live
     * one lets a provider reclassified after a billing surprise sort correctly
     * without a code change.
     */
    private fun weightOf(candidate: ProviderEligibility): Int =
        candidate.quota?.quotaWeight ?: candidate.provider.quotaTier

    /**
     * Remaining quota, descending.
     *
     * An unknown remaining sorts as unlimited rather than as zero. A provider
     * that does not report quota is not a provider that has none, and treating
     * silence as exhaustion would push every non-reporting provider to the back
     * of every chain.
     */
    private fun remainingEstimate(candidate: ProviderEligibility): Long {
        val quota = candidate.quota ?: return Long.MAX_VALUE
        if (quota.state == ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET) return 0
        return Long.MAX_VALUE - quota.usedRequests.toLong().coerceAtLeast(0)
    }

    /**
     * How likely this provider is to cool down again soon, in `[0.0, 1.0]`.
     *
     * The sixth term. Derived from the current state and success score rather
     * than stored separately, so it cannot drift from the record it summarises.
     * A provider currently in cooldown that has become eligible again carries
     * the most risk of returning there.
     */
    fun cooldownRisk(candidate: ProviderEligibility): Double {
        val quota = candidate.quota ?: return 0.0
        val stateRisk = when (quota.state) {
            ProviderAvailabilityState.COOLDOWN -> 1.0
            ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET -> 0.9
            ProviderAvailabilityState.DEGRADED -> 0.5
            ProviderAvailabilityState.MODEL_MISSING -> 0.4
            ProviderAvailabilityState.UNKNOWN -> 0.2
            else -> 0.0
        }
        val scoreRisk = 1.0 - quota.successScore.coerceIn(0.0, 1.0)
        return ((stateRisk + scoreRisk) / 2).coerceIn(0.0, 1.0)
    }

    /**
     * The ordering as text, for a route explanation.
     *
     * Blueprint Phase 3 requires an explanation to show every selected and
     * skipped provider. Showing the order *and the term that decided it* is
     * what turns "it chose Groq" into something an operator can argue with.
     */
    fun explain(ordered: List<ProviderEligibility>): String =
        ordered.joinToString(" > ") { candidate ->
            buildString {
                append(candidate.provider.id)
                append("(w=").append(weightOf(candidate))
                candidate.quota?.let {
                    append(" s=%.2f".format(it.successScore))
                    it.latencyMsAvg?.let { latency -> append(" ").append(latency).append("ms") }
                }
                append(')')
            }
        }
}
