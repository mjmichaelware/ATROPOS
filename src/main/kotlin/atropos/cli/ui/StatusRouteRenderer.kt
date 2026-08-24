/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.provider.adapter.AdapterRouteResult
import atropos.core.security.RedactionFilter
import java.util.Locale

class StatusRouteRenderer(
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager()),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val surface get() = theme.surface

    fun renderRoute(result: AdapterRouteResult, width: Int): List<String> {
        val decision = result.decision
        val body = buildList {
            add(surface.statusRow("selected provider", decision.selectedProviderId ?: "local_degraded", if (decision.selectedProviderId != null) Health.VERIFIED else Health.ERROR, width))
            add(surface.row("task capability", decision.task.capability.name.lowercase(Locale.US), width))
            add(surface.row("cost policy", "cost_conscious", width))
            add(surface.row("adapter", result.adapterStatus?.providerId ?: "none", width))
            add(surface.row("note", redactionFilter.redact(result.note), width))

            add(surface.sectionHeading("ELIGIBLE PROVIDERS", width))
            if (decision.eligible.isEmpty()) {
                add(surface.hint("  none", width))
            } else {
                decision.eligible.forEach { eligible ->
                    val health = when (eligible.quota?.state?.name?.lowercase(Locale.US)) {
                        "ready", "configured" -> Health.VERIFIED
                        "unknown" -> Health.UNKNOWN
                        else -> Health.PENDING
                    }
                    add(surface.statusRow(eligible.provider.id, "reason=${redactionFilter.redact(eligible.reason)}", health, width))
                }
            }

            add(surface.sectionHeading("SKIPPED PROVIDERS", width))
            if (decision.skipped.isEmpty()) {
                add(surface.hint("  none", width))
            } else {
                decision.skipped.forEach { skipped ->
                    add(surface.statusRow(skipped.provider.id, "reason=${redactionFilter.redact(skipped.reason)}", Health.ERROR, width))
                }
            }
        }
        return surface.block("ROUTING DECISION", body, width, Role.BRAND)
    }
}
