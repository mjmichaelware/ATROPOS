/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.provider.ProviderOnboardingService
import atropos.core.provider.StaticProviderDescriptorRegistry

/**
 * Compact provider summary for status line and other space-constrained surfaces.
 *
 * F-CLI-006: Providers one-line healthy summary.
 * Default compact line; full matrix on expand or /providers full.
 */
class StatusProviderSummaryRenderer(
    private val theme: TerminalTheme
) {
    fun render(registry: StaticProviderDescriptorRegistry, onboarding: ProviderOnboardingService, width: Int): String {
        val rows = onboarding.list()
        val healthy = rows.filter { it.health == atropos.core.provider.CheapProviderHealth.HEALTHY && !it.disabled }
        val disabled = rows.count { it.disabled }
        val total = rows.size

        if (healthy.isEmpty()) {
            return theme.subdued("providers: ${total} healthy=0 disabled=$disabled")
        }

        val healthyIds = healthy.map { it.providerId }
        val preferred = rows.filter { it.preferred && it.health == atropos.core.provider.CheapProviderHealth.HEALTHY && !it.disabled }
            .map { it.providerId }
        val cascade = atropos.core.provider.ProviderCascadeOrder.order(
            healthy.map { it.providerId },
            atropos.core.provider.StaticProviderDescriptorRegistry()
        )
        val candidates = cascade.joinToString(" -> ").ifBlank { "none" }

        val paid = healthy
            .map { it.providerId }
            .filter { StaticProviderDescriptorRegistry().getById(it)?.isPaid() == true }
            .joinToString(" -> ")
            .ifBlank { "none" }

        return buildString {
            append("providers: healthy=${healthy.size}/$total ")
            if (candidates.isNotBlank()) append("cascade=$candidates ")
            if (preferred.isNotEmpty()) append("preferred=${preferred.joinToString(",")} ")
            if (rows.any { it.disabled }) append("disabled=${rows.count { it.disabled }} ")
        }.trim()
    }
}