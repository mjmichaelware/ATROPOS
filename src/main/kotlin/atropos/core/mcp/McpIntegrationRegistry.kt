/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * MCP Integration Registry
 *
 * Central registry for all MCP integrations.
 * Manages registration, discovery, and routing.
 */
package atropos.core.mcp

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class McpIntegrationRegistry(configDir: Path) {

    private val integrations = ConcurrentHashMap<String, McpIntegration>()
    private val configDir = configDir.resolve("mcp").apply { Files.createDirectories(this) }

    /**
     * Registers an MCP integration.
     */
    fun register(integration: McpIntegration): Boolean {
        return integrations.putIfAbsent(integration.systemId, integration) == null
    }

    /**
     * Gets an integration by system ID.
     */
    fun get(systemId: String): McpIntegration? = integrations[systemId]

    /**
     * Gets all registered integrations.
     */
    fun getAll(): List<McpIntegration> = integrations.values.toList()

    /**
     * Removes an integration.
     */
    fun unregister(systemId: String): Boolean = integrations.remove(systemId) != null

    /**
     * Initializes built-in integrations.
     */
    fun initializeBuiltins() {
        // These would be instantiated with actual config
        // register(GitLabMcpIntegration(configDir.resolve("gitlab")))
        // register(GitHubMcpIntegration(configDir.resolve("github")))
        // register(JiraMcpIntegration(configDir.resolve("jira")))
        // register(LinearMcpIntegration(configDir.resolve("linear")))
        // register(DockerMcpIntegration(configDir.resolve("docker")))
        // register(PostgresMcpIntegration(configDir.resolve("postgres")))
    }

    /**
     * Gets all registration info for discovery.
     */
    fun discoverAll(): List<RegistrationInfo> {
        return integrations.values.map { it.register() }
    }

    /**
     * Gets an integration that supports a capability.
     */
    fun findByCapability(capability: String): List<McpIntegration> {
        return integrations.values.filter { capability in it.register().capabilities }
    }

    /**
     * Persists registry state to CAS.
     */
    fun persistRegistry(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("mcp-registry.json")
        val json = integrations.values.map { i ->
            val reg = i.register()
            """
                {
                    "systemId": "${reg.systemId}",
                    "displayName": "${reg.displayName}",
                    "capabilities": [${reg.capabilities.joinToString(", ") { "\"$it\"" }}],
                    "authRequired": [${reg.authRequired.joinToString(", ") { "\"$it\"" }}],
                    "version": "${reg.version}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to list integrations.
     */
    fun listIntegrations(): String {
        return integrations.values.map { i ->
            val reg = i.register()
            "${reg.systemId} (${reg.displayName}): ${reg.capabilities.joinToString(", ")}"
        }.joinToString("\n")
    }
}