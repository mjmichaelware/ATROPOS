/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import org.json.JSONObject

/**
 * ADD-MCP-004: Disabled-by-Default Community Server Policy Governor
 * Section 2.1 mandate: Community servers default to disabled unless explicitly
 * opted-in by the human operator. Core platform tools (git, filesystem) default to enabled.
 */

data class GovernedServerDescriptor(
    val serverId: String,
    val isCoreTool: Boolean,
    val isExplicitlyEnabled: Boolean,
    val securityTier: Int,
    val requiresNetwork: Boolean
)

class CommunityServerPolicyGovernor(val configRoot: Path) {
    private val CORE_SERVERS = setOf("local_git", "filesystem", "core_memory")

    fun loadGovernedServers(): List<GovernedServerDescriptor> {
        val configFile = configRoot.resolve("mcp.json").toFile()
        if (!configFile.exists()) return emptyList()

        return try {
            val root = JSONObject(configFile.readText(StandardCharsets.UTF_8))
            val servers = root.optJSONObject("mcpServers") ?: return emptyList()
            servers.keySet().map { key ->
                val obj = servers.getJSONObject(key)
                val isCore = key in CORE_SERVERS
                val explicitlyEnabled = obj.optBoolean("enabled", isCore)
                val tier = obj.optInt("tier", if (isCore) 1 else 3)
                val reqNetwork = obj.optBoolean("requiresNetwork", false)
                GovernedServerDescriptor(
                    serverId = key,
                    isCoreTool = isCore,
                    isExplicitlyEnabled = explicitlyEnabled,
                    securityTier = tier,
                    requiresNetwork = reqNetwork
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun assertServerExecutionAllowed(serverId: String) {
        val servers = loadGovernedServers()
        val server = servers.find { it.serverId == serverId }
            ?: throw SecurityException("MCP Server [$serverId] is not registered in configuration")

        if (!server.isExplicitlyEnabled && !server.isCoreTool) {
            throw SecurityException("MCP Community Server [$serverId] execution refused: Server is disabled by policy (requires explicit opt-in)")
        }
    }

    fun setServerActivation(serverId: String, enabled: Boolean): Boolean {
        val configFile = configRoot.resolve("mcp.json").toFile()
        if (!configFile.exists()) return false

        return try {
            val text = configFile.readText(StandardCharsets.UTF_8)
            val root = JSONObject(text)
            val servers = root.optJSONObject("mcpServers") ?: return false
            if (!servers.has(serverId)) return false

            val serverObj = servers.getJSONObject(serverId)
            serverObj.put("enabled", enabled)
            configFile.writeText(root.toString(2), StandardCharsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }
}
