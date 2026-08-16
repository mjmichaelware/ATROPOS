/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/** Thin surface facade over the canonical disclosure state machine. */
object ProgressiveDisclosure {
    fun expand(row: DisclosureRow): DisclosureRow.Expansion? = row.expand()

    fun visible(row: DisclosureRow): List<String> =
        row.content.visibleAt(row.state.revealed ?: DisclosureLevel.SHALLOWEST)

    /** Full repaint path delegated to the canonical disclosure formatter. */
    fun render(row: DisclosureRow, style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT): List<String> =
        DisclosureRowFormatter.render(row, style)
}
