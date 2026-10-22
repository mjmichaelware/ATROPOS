/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.mcp.discovery

import atropos.core.integration.McpRegistryDiscoveryService
import atropos.core.integration.RegistrySearchResult

/**
 * Safe Registry Discovery Tool alias in atropos.mcp.discovery package.
 */
class SafeRegistryDiscoveryTool(
    private val delegate: McpRegistryDiscoveryService = McpRegistryDiscoveryService()
) {
    fun findServers(query: String): List<RegistrySearchResult> = delegate.searchCatalog(query)
    fun generateJson(result: RegistrySearchResult): String = delegate.formatMcpJsonSnippet(result)
}
