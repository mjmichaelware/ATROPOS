/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.chrome.CheckpointAge

/**
 * Checkpoint chip for the status bar footer.
 *
 * F-CLI-004: Checkpoint chip + resume panel.
 * Shows checkpoint age; one primary Continue action.
 */
class CheckpointChipRenderer(
    private val theme: TerminalTheme
) {
    fun render(age: CheckpointAge, width: Int): String {
        val label = when (age) {
            is CheckpointAge.Known -> "checkpoint ${age.label()}"
            CheckpointAge.Unknown -> "checkpoint unknown"
            CheckpointAge.Skewed -> "checkpoint clock skew"
        }
        val icon = "🏁"
        return theme.paint(atropos.cli.ui.design.Role.STATUS_COMPLETE, "🏁 $label")
    }

    fun renderChip(age: CheckpointAge, width: Int): String {
        val label = when (age) {
            is CheckpointAge.Known -> age.label()
            CheckpointAge.Unknown -> "unknown"
            CheckpointAge.Skewed -> "skew"
        }
        return theme.subdued("[🏁 $label]")
    }
}