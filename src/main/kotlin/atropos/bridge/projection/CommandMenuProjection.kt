/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter

/** One selectable action in a graphical surface. */
data class MenuAction(
    val id: String,
    val label: String,
    val group: String,
    val description: String,
    /** True when choosing this changes state and should confirm first. */
    val mutating: Boolean,
    /** Free-text the operator must supply, or null when it takes none. */
    val argumentHint: String?
)

/**
 * The command surface as something you pick from, not something you type.
 *
 * A graphical client is an *alternative* to the CLI, not a reproduction of it.
 * Making a phone user type `/agent self-host recover` reproduces the CLI's
 * discovery problem on a surface with no tab-completion, no history and a
 * worse keyboard — and it makes every command invisible until someone already
 * knows it exists. The same capability presented as grouped, labelled actions
 * is discoverable by looking.
 *
 * Derived from the command registry rather than declared here. A second
 * hand-maintained list would drift from the CLI the first time a command was
 * added, and the two surfaces would disagree about what the engine can do.
 *
 * [mutating] exists so a client can require confirmation for actions that
 * change state. On a phone a mis-tap is one pixel away from a deliberate
 * choice, and "run the queue" should not be as easy to trigger by accident as
 * "show status".
 */
class CommandMenuProjection {

    fun render(commands: List<MenuAction>): String = JsonWriter.obj(
        "count" to JsonWriter.num(commands.size.toLong()),
        "groups" to JsonWriter.arr(
            commands.groupBy { it.group }
                .toSortedMap()
                .map { (group, actions) ->
                    JsonWriter.obj(
                        "group" to JsonWriter.str(group),
                        "actions" to JsonWriter.arr(actions.map(::actionJson))
                    )
                }
        )
    )

    private fun actionJson(action: MenuAction): String = JsonWriter.obj(
        "id" to JsonWriter.str(action.id),
        "label" to JsonWriter.str(action.label),
        "group" to JsonWriter.str(action.group),
        "description" to JsonWriter.str(action.description),
        "mutating" to JsonWriter.bool(action.mutating),
        "argumentHint" to JsonWriter.str(action.argumentHint.orEmpty())
    )
}
