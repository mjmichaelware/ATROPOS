/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.territory.TerritoryAssignment

/** Projects the existing territory grant into a first-class surface material. */
class TerritoryAsMaterial {
    data class Material(
        val owner: String,
        val prefix: String,
        val state: String,
        val readOnly: Boolean
    )

    fun material(assignment: TerritoryAssignment): Material = Material(
        owner = assignment.ownerId,
        prefix = assignment.allowedPrefix,
        state = if (assignment.readOnly) "read-only" else "writable",
        readOnly = assignment.readOnly
    )

    fun render(assignment: TerritoryAssignment, width: Int): String {
        val value = material(assignment)
        val body = "territory ${value.prefix} · ${value.owner} · ${value.state}"
        return TerminalText.ellipsize(body, width.coerceAtLeast(1))
    }
}
