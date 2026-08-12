/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/** Persistent, compact recovery state for the operator status surface. */
class RecoveryTectonicRibbon {
    data class State(
        val continuity: String,
        val freeSpace: String,
        val authorization: String
    )

    fun render(state: State, width: Int): String {
        val text = "recovery · ${state.continuity} · free ${state.freeSpace} · auth ${state.authorization}"
        return TerminalText.ellipsize(text, width.coerceAtLeast(1))
    }
}
