/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.auth.CascadeResolution
import atropos.core.auth.AuthVerifyResult

class StatusAuthRenderer(
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun renderVerify(statuses: List<AuthVerifyResult>, width: Int): List<String> {
        if (statuses.isEmpty()) {
            return listOf(
                surface.hint("No authority documents found. Declare AGENTS.md in repo root.", width)
            )
        }
        val body = statuses.map { status ->
            val health = when (status.state.lowercase()) {
                "verified" -> Health.VERIFIED
                "unattested", "dirty" -> Health.PENDING
                "refused" -> Health.ERROR
                else -> Health.UNKNOWN
            }
            surface.statusRow(status.path, "${status.state} (${status.sha256.take(8)})", health, width)
        }
        return surface.block("AUTHORITY DOCUMENTS", body, width, Role.BRAND)
    }

    fun renderCascade(resolutions: List<CascadeResolution>, width: Int): List<String> {
        val body = resolutions.map { resolution ->
            val health = when (resolution) {
                is CascadeResolution.Resolved -> Health.VERIFIED
                is CascadeResolution.Violation -> Health.ERROR
                is CascadeResolution.Undefined -> Health.UNKNOWN
            }
            val text = when (resolution) {
                is CascadeResolution.Resolved -> {
                    val suffix = if (resolution.final) " (final)" else ""
                    "${resolution.value} [${resolution.source}]$suffix"
                }
                is CascadeResolution.Violation -> "REFUSED: ${resolution.reason}"
                is CascadeResolution.Undefined -> "undefined"
            }
            surface.statusRow(resolution.key, text, health, width)
        }
        return surface.block("AUTHORITY CASCADE", body, width, Role.BRAND)
    }
}
