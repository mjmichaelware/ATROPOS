/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * Disclosure row for collapsible transcript sections.
 *
 * `F-CLI-002`: Disclosure rows for Thinking/Plan/Evidence/Engine/Checkpoint.
 * Default collapsed (L1), expand adds detail only (L2/L3).
 */
enum class DisclosureKind(val label: String, val icon: String) {
    THINKING("Thinking", "🧠"),
    PLAN("Plan", "📋"),
    EVIDENCE("Evidence", "📎"),
    ENGINE("Engine", "⚙️"),
    CHECKPOINT("Checkpoint", "🏁")
}

data class DisclosureRow(
    val kind: DisclosureKind,
    val summary: String,
    val detail: String,
    var isExpanded: Boolean = false
) {
    fun toggle(): DisclosureRow = copy(isExpanded = !isExpanded)
}