/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

/**
 * The five disclosure rows HOE-B02 names, in the order it names them:
 * Thinking · Plan · Evidence · Engine · Checkpoint.
 *
 * The set is closed and the order is fixed because these rows are a *vocabulary*
 * — the same five labels in the same sequence under every transcript entry.
 * Rows that appear in a different order per entry, or whose labels drift between
 * views ("Reasoning" here, "Thinking" there), force the reader to re-scan the
 * block every time instead of learning one shape once. Making this an enum means
 * a caller cannot invent a sixth row locally, and cannot reorder them by
 * building the list in the wrong sequence — [ordered] is the only order.
 *
 * A row being in this enum does not mean it is always drawn. A transcript entry
 * with no evidence has no Evidence row; see [DisclosureRowSet], which omits
 * absent rows rather than drawing an empty one, since an expandable row that
 * reveals nothing is worse than no row at all.
 */
enum class DisclosureRowKind(
    /** Exact label text from HOE-B02. Renderers must not re-word these. */
    val label: String
) {
    /** The model's reasoning trace. First because it precedes everything else. */
    THINKING("Thinking"),

    /** The intended sequence of steps, before execution. */
    PLAN("Plan"),

    /** Citations, file reads, tool output — what the claims rest on. */
    EVIDENCE("Evidence"),

    /** Provider, model, routing and cost — which machinery actually ran. */
    ENGINE("Engine"),

    /** Verification and gate results — whether this entry is trustworthy. */
    CHECKPOINT("Checkpoint");

    companion object {
        private val ORDERED: List<DisclosureRowKind> = values().toList()

        /** Canonical presentation order. Callers must not re-sort this. */
        fun ordered(): List<DisclosureRowKind> = ORDERED
    }
}
