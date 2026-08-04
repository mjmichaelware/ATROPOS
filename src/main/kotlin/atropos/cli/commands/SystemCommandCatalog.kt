/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

/**
 * The names and one-line summaries of the phase subsystem commands.
 *
 * Held apart from the dispatcher so that the catalog can be read — by help
 * rendering, by tests asserting a command is reachable — without constructing
 * the twelve services the dispatcher wires up. A help text that can only be
 * obtained by instantiating the whole subsystem tree is a help text that gets
 * out of date, because nothing cheap can check it.
 */
object SystemCommandCatalog {

    /** One row per routable subsystem command, in phase order. */
    val entries: List<SystemCommandEntry> = listOf(
        SystemCommandEntry(12, "director", "observe|report|acknowledge|dismiss|scan"),
        SystemCommandEntry(13, "territory", "assign|revoke|violations|resolve"),
        SystemCommandEntry(14, "hr", "route|audit"),
        SystemCommandEntry(15, "auditor", "run"),
        SystemCommandEntry(15, "custodian", "clean|prune"),
        SystemCommandEntry(16, "hierarchy", "register|snapshot|escalate"),
        SystemCommandEntry(16, "dag", "status|ingest|runnable|cycles|hig|snapshot"),
        SystemCommandEntry(17, "snapshot", "capture|compare|list"),
        SystemCommandEntry(17, "inspect", "file|dag|viewport|full|report"),
        SystemCommandEntry(18, "platform", "adapters|health|env"),
        SystemCommandEntry(19, "artifact", "plan|build|verify|install|commit|gate"),
        SystemCommandEntry(20, "autonomous", "init|tick|run|run-max|backlog|repairs|failovers")
    )

    /** The command words the dispatcher accepts, without their leading slash. */
    val commandNames: Set<String> = entries.map { it.name }.toSet()

    fun render(): String = buildString {
        appendLine("System commands (all phases):")
        entries.forEach { entry ->
            appendLine("  PHASE ${entry.phase}: /${entry.name} ${entry.subcommands}")
        }
    }.trimEnd()
}

/**
 * @param phase the blueprint phase the command belongs to, shown so an operator
 *   can tell which surfaces are expected to be complete.
 */
data class SystemCommandEntry(
    val phase: Int,
    val name: String,
    val subcommands: String
)
