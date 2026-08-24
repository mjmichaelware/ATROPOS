package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.paid.EmergencyPaidGate
import atropos.core.security.RedactionFilter

class StatusPaidEmergencyRenderer(
    private val gate: EmergencyPaidGate = EmergencyPaidGate(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager()),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val surface get() = theme.surface

    fun render(): String = render(80).joinToString("\n")

    fun render(width: Int): List<String> {
        val status = gate.status()
        val active = status.active
        val body = buildList {
            if (active == null) {
                add(surface.statusRow("state", "locked", Health.ERROR, width))
                add(surface.hint("unlock: /paid unlock <provider> <duration> reason=\"...\"", width))
            } else {
                add(surface.statusRow("state", "unlocked", Health.VERIFIED, width))
                add(surface.row("provider", active.providerId, width))
                add(surface.row("expires", active.expiresAtEpochMs.toString(), width))
                add(surface.row("reason", redactionFilter.redact(active.reason), width))
            }
            add(surface.row("paid providers", status.knownPaidProviders.joinToString(", "), width))
            add(surface.row("audit", status.auditFile.name, width))
            add(surface.hint("policy: paid providers are never selected automatically", width))
        }
        return surface.block("PAID EMERGENCY GATE", body, width, Role.BRAND)
    }
}
