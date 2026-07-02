package atropos.cli.ui

import atropos.core.paid.EmergencyPaidGate

class StatusPaidEmergencyRenderer(
    private val gate: EmergencyPaidGate = EmergencyPaidGate()
) {
    fun render(): String {
        val status = gate.status()
        return buildString {
            appendLine("paid emergency:")
            if (status.active == null) {
                appendLine("  state: locked")
                appendLine("  unlock: /paid unlock <provider> <duration> reason=\"...\"")
            } else {
                appendLine("  state: unlocked")
                appendLine("  provider: ${status.active.providerId}")
                appendLine("  expires_at_epoch_ms: ${status.active.expiresAtEpochMs}")
                appendLine("  reason: ${status.active.reason}")
            }
            appendLine("  providers: ${status.knownPaidProviders.joinToString(",")}")
            appendLine("  audit: ${status.auditFile.path}")
            appendLine("  policy: paid providers are never selected automatically")
        }
    }
}
