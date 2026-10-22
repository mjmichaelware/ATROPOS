/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.supremacy.OpenCoreAirGapController
import org.json.JSONObject

/**
 * ADD-MCP-005: Safe Registry Discovery Tool under Bounded Agency Gate
 * Section 2.1 mandate: Search MCP capabilities without downloading unvetted repositories
 * or spawning untrusted subprocesses. Air-gap disablement blocks search when local-only.
 */

data class RegistrySearchResult(
    val serverName: String,
    val description: String,
    val vendor: String,
    val suggestedCommand: String,
    val suggestedArgs: List<String>,
    val verifiedSecurity: Boolean
)

class McpRegistryDiscoveryService(
    val airGapController: OpenCoreAirGapController = OpenCoreAirGapController
) {
    // Static vetted catalog of standard MCP tools
    private val staticCatalog = listOf(
        RegistrySearchResult(
            serverName = "git",
            description = "Local git repository manipulation and branch inspection",
            vendor = "Atropos Core",
            suggestedCommand = "git",
            suggestedArgs = listOf("mcp-server"),
            verifiedSecurity = true
        ),
        RegistrySearchResult(
            serverName = "filesystem",
            description = "Sandboxed filesystem tool bounded to declared workspace territory",
            vendor = "Atropos Core",
            suggestedCommand = "fs-tool",
            suggestedArgs = listOf("--sandbox"),
            verifiedSecurity = true
        ),
        RegistrySearchResult(
            serverName = "postgres",
            description = "Read-only schema and table inspection for PostgreSQL databases",
            vendor = "Community (Audited)",
            suggestedCommand = "npx",
            suggestedArgs = listOf("-y", "@modelcontextprotocol/server-postgres"),
            verifiedSecurity = false
        ),
        RegistrySearchResult(
            serverName = "github",
            description = "GitHub issues and pull request query provider",
            vendor = "Community (Audited)",
            suggestedCommand = "npx",
            suggestedArgs = listOf("-y", "@modelcontextprotocol/server-github"),
            verifiedSecurity = false
        )
    )

    fun searchCatalog(query: String): List<RegistrySearchResult> {
        val mode = airGapController.currentMode()
        if (mode.isLocalOnly || mode.airGapped) {
            // Air-gap mandate: return empty list with local-only constraint
            return emptyList()
        }

        val q = query.lowercase().trim()
        if (q.isBlank()) return staticCatalog

        return staticCatalog.filter {
            it.serverName.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.vendor.lowercase().contains(q)
        }
    }

    fun formatMcpJsonSnippet(result: RegistrySearchResult): String {
        val root = JSONObject()
        val serverObj = JSONObject()
        serverObj.put("command", result.suggestedCommand)
        serverObj.put("args", result.suggestedArgs)
        serverObj.put("enabled", false) // Enforce disabled-by-default policy
        serverObj.put("community", !result.verifiedSecurity)
        root.put(result.serverName, serverObj)
        return root.toString(2)
    }
}
