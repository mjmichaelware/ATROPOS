/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

/**
 * Routes `/director`, `/territory`, `/hr`, … to the handler that owns each.
 *
 * This class used to be all twelve subsystems at once: a single 484-line file
 * holding the director's vocabulary, the territory rules, snapshot formatting,
 * artifact promotion, and nine other concerns that share nothing but a command
 * prefix. Every one of them had to be read to change any one of them.
 *
 * What is left here is dispatch and nothing else. The handlers are constructed
 * lazily so that typing `/platform` does not build a director store, a DAG
 * service, and an artifact pipeline on the way to reporting the host's OS
 * version — construction of those services touches disk, and paying for twelve
 * of them per command was a cost no operator asked for.
 *
 * Unknown commands echo the catalog rather than a bare error, because the most
 * common cause of one is not knowing what exists.
 */
class HierarchyCommand {

    private val director by lazy { DirectorCommandHandler() }
    private val territory by lazy { TerritoryCommandHandler() }
    private val hr by lazy { HrCommandHandler() }
    private val auditor by lazy { AuditorCommandHandler() }
    private val custodian by lazy { CustodianCommandHandler() }
    private val hierarchy by lazy { HierarchyRegistryCommandHandler() }
    private val dag by lazy { DagCommandHandler() }
    private val snapshot by lazy { SnapshotCommandHandler() }
    private val inspect by lazy { InspectCommandHandler() }
    private val platform by lazy { PlatformCommandHandler() }
    private val artifact by lazy { ArtifactCommandHandler() }
    private val autonomous by lazy { AutonomousCommandHandler() }

    fun execute(tokens: List<String>): String {
        val command = tokens.firstOrNull()?.removePrefix("/") ?: return SystemCommandCatalog.render()
        val args = tokens.drop(1)

        return when (command) {
            "director" -> director.handle(args)
            "territory" -> territory.handle(args)
            "hr" -> hr.handle(args)
            "auditor" -> auditor.handle(args)
            "custodian" -> custodian.handle(args)
            "hierarchy" -> hierarchy.handle(args)
            "dag" -> dag.handle(args)
            "snapshot" -> snapshot.handle(args)
            "inspect" -> inspect.handle(args)
            "platform" -> platform.handle(args)
            "artifact" -> artifact.handle(args)
            "autonomous" -> autonomous.handle(args)
            else -> "unknown command: /$command\n${SystemCommandCatalog.render()}"
        }
    }
}
