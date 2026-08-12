/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/** Deterministic semantic styling choice for the active interaction mode. */
class ModeRetheme {
    data class ModeStyle(val label: String, val role: Role)

    fun style(mode: String): ModeStyle = when (mode.trim().uppercase()) {
        "PLAN" -> ModeStyle("plan", Role.INFO)
        "BUILD", "FACTORY" -> ModeStyle("build", Role.STATUS_PENDING)
        "AGENT", "SELF-HOST" -> ModeStyle("agent", Role.STATUS_VERIFIED)
        else -> ModeStyle(mode.trim().lowercase().ifBlank { "ask" }, Role.ACCENT_FOCUS)
    }
}
