/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusPaidEmergencyRenderer
import atropos.core.paid.EmergencyPaidGate

class PaidCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val gate: EmergencyPaidGate = EmergencyPaidGate()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderNotice(StatusPaidEmergencyRenderer(gate).render())
            "unlock" -> unlock(tokens)
            "lock" -> uiEngine.renderNotice(if (gate.lock()) "paid emergency locked" else "paid emergency already locked")
            else -> uiEngine.renderError("usage: /paid [status|unlock <provider> <duration> reason=\"...\"|lock]")
        }
        return RouterOutcome.CONTINUE
    }

    private fun unlock(tokens: List<String>) {
        val provider = tokens.getOrNull(2)
        val duration = tokens.getOrNull(3)
        val reason = tokens.drop(4).joinToString(" ").removePrefix("reason=").ifBlank { "manual emergency unlock" }
        if (provider == null || duration == null) {
            uiEngine.renderError("usage: /paid unlock <provider> <duration> reason=\"...\"")
            return
        }
        try {
            val unlock = gate.unlock(provider, duration, reason)
            uiEngine.renderNotice("unlocked ${unlock.providerId} until ${unlock.expiresAtEpochMs}")
        } catch (failure: IllegalArgumentException) {
            uiEngine.renderError(failure.message ?: "paid unlock failed")
        }
    }
}
