/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * ATOM 6.6: Catalog Command Synchronization and Alignment.
 *
 * Maintains a synchronized registry of all executable commands, categories, aliases,
 * and risk classifications across Atropos CLI, Web, and Bridge services.
 *
 * ## Research: Automated Compile-Time Annotation Processing
 * Research automated compile-time annotation processing (KSP / kapt) to generate
 * command catalogs directly from command handler annotations rather than maintaining
 * parallel declarations, guaranteeing surface parity and compile-time contract safety.
 */
object SynchronizedCommandCatalogRegistry {

    val catalog: List<CommandEntry> get() = CommandCatalog.catalog

    fun findCommand(commandName: String): CommandEntry? {
        val normalized = commandName.trim().lowercase()
        return catalog.firstOrNull { entry ->
            entry.command.lowercase() == normalized || entry.aliases.any { it.lowercase() == normalized }
        }
    }

    fun commandsByCategory(): Map<String, List<CommandEntry>> =
        catalog.groupBy { it.category }

    fun commandsWithRisk(risk: CommandRisk): List<CommandEntry> =
        catalog.filter { it.risk == risk }

    fun totalCommandCount(): Int = catalog.size
}
