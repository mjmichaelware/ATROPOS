/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.mcp.catalog

import atropos.core.integration.McpCatalogConfigHarness
import atropos.core.integration.McpCatalogEntry
import java.nio.file.Path

/**
 * DeclarativeMcpCatalogRegistry alias in atropos.mcp.catalog package.
 */
class DeclarativeMcpCatalogRegistry(configPath: Path) {
    private val harness = McpCatalogConfigHarness(configPath)

    fun loadAll(): List<McpCatalogEntry> = harness.loadCatalogEntries()
    fun generateConfig(tiers: Set<Int>, localOnly: Boolean) = harness.generateVerifiedMcpJson(tiers, localOnly)
}
