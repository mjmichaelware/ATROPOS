/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject

/**
 * ADD-MCP-EX-001..013: Declarative Multi-Tier MCP Catalog Allowlist Harness
 * Section 2.2 mandate: Community and standard MCP tools are integrated strictly
 * via declarative JSON configurations rather than per-server Kotlin wrapper classes.
 */

data class McpCatalogEntry(
    val id: String,
    val tier: Int,
    val command: String,
    val defaultArgs: List<String>,
    val requiredEnvKeys: List<String>,
    val defaultEnabled: Boolean,
    val readOnly: Boolean
)

class McpCatalogConfigHarness(val configPath: Path) {

    fun loadCatalogEntries(): List<McpCatalogEntry> {
        if (!Files.exists(configPath)) {
            return emptyList()
        }
        val text = Files.readString(configPath, StandardCharsets.UTF_8)
        val root = JSONObject(text)
        val arr = root.optJSONArray("servers") ?: return emptyList()

        val results = mutableListOf<McpCatalogEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val argsArr = obj.optJSONArray("defaultArgs") ?: JSONArray()
            val args = (0 until argsArr.length()).map { argsArr.getString(it) }

            val envArr = obj.optJSONArray("requiredEnvKeys") ?: JSONArray()
            val env = (0 until envArr.length()).map { envArr.getString(it) }

            results.add(
                McpCatalogEntry(
                    id = obj.getString("id"),
                    tier = obj.optInt("tier", 1),
                    command = obj.getString("command"),
                    defaultArgs = args,
                    requiredEnvKeys = env,
                    defaultEnabled = obj.optBoolean("defaultEnabled", false),
                    readOnly = obj.optBoolean("readOnly", false)
                )
            )
        }
        return results
    }

    fun validateServerPrerequisites(entry: McpCatalogEntry): Pair<Boolean, String?> {
        for (envKey in entry.requiredEnvKeys) {
            val v = System.getenv(envKey)
            if (v.isNullOrBlank()) {
                return Pair(false, "Missing required environment variable: $envKey")
            }
        }
        return Pair(true, null)
    }

    fun generateVerifiedMcpJson(activeTiers: Set<Int>, localOnly: Boolean): String {
        val entries = loadCatalogEntries()
        val serversRoot = JSONObject()

        for (entry in entries) {
            if (entry.tier !in activeTiers) continue

            // If localOnly is active, exclude servers that require outbound external API keys
            if (localOnly && entry.requiredEnvKeys.any { it.contains("API_KEY") }) {
                continue
            }

            val serverObj = JSONObject()
            serverObj.put("command", entry.command)
            serverObj.put("args", entry.defaultArgs)
            serverObj.put("tier", entry.tier)
            serverObj.put("enabled", entry.defaultEnabled)
            serverObj.put("readOnly", entry.readOnly)

            serversRoot.put(entry.id, serverObj)
        }

        val configRoot = JSONObject()
        configRoot.put("mcpServers", serversRoot)
        return configRoot.toString(2)
    }
}
