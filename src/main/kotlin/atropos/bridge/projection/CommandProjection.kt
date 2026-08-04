/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.cli.input.CommandRegistry

/**
 * Projects the command registry onto the wire.
 *
 * `HOE-A07` requires the palette to reach every primary action and
 * `SUP.UX.COMMAND-REGISTRY` requires one source of truth for it — "generate
 * palette from single command registry; never hard-code entries". A Web palette
 * with its own list is that hard-coded entry set, one process away; it drifts
 * the moment a command is added, and the drift is invisible because both sides
 * look internally consistent.
 *
 * So this file holds no command names. It reads [CommandRegistry] — the same
 * object the CLI palette and `/help` read — and serialises it. `SUP.UX.HELP-GENERATOR`
 * is served by the same call: [CommandRegistry.helpSections] is the grouped
 * form, so Web help and CLI help are the same grouping by construction rather
 * than by agreement.
 */
class CommandProjection {

    fun render(): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "commands" to JsonWriter.arr(
            CommandRegistry.entries.map { entry ->
                JsonWriter.obj(
                    "command" to JsonWriter.str(entry.command),
                    "description" to JsonWriter.str(entry.description)
                )
            }
        ),
        "quickAccess" to JsonWriter.strArr(CommandRegistry.quickAccessCommands()),
        "families" to JsonWriter.strArr(CommandRegistry.families().sorted()),
        "sections" to JsonWriter.arr(
            CommandRegistry.helpSections().map { group ->
                JsonWriter.obj(
                    "category" to JsonWriter.str(group.category),
                    "entries" to JsonWriter.arr(
                        group.entries.map {
                            JsonWriter.obj(
                                "command" to JsonWriter.str(it.command),
                                "description" to JsonWriter.str(it.description)
                            )
                        }
                    )
                )
            }
        )
    )
}
