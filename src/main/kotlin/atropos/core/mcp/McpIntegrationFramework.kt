/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * MCP Integration Base Framework
 *
 * Base classes and interfaces for all MCP integrations.
 * Each integration implements 7 micro-atoms:
 * -auth: Authentication/credential management
 * -list: List resources
 * -get: Get single resource
 * -mutate: Create/update/delete resources
 * -reg: Registration/discovery
 * -terr: Territory/boundary enforcement
 * -sec: Security/secret handling
 */
package atropos.core.mcp

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Base interface for all MCP integrations.
 */
interface McpIntegration {
    val systemId: String
    val systemName: String

    // -auth: Authentication
    fun authenticate(credentials: Map<String, String>): AuthResult
    fun refreshToken(): AuthResult
    fun revokeAccess(): Boolean

    // -list: List resources
    fun listResources(params: Map<String, String> = emptyMap()): List<McpResource>

    // -get: Get single resource
    fun getResource(id: String, params: Map<String, String> = emptyMap()): McpResource?

    // -mutate: Create/update/delete
    fun createResource(resource: McpResource): McpResource
    fun updateResource(id: String, updates: Map<String, Any>): McpResource
    fun deleteResource(id: String): Boolean

    // -reg: Registration
    fun register(): RegistrationInfo
    fun discover(): List<RegistrationInfo>
    fun unregister(): Boolean

    // -terr: Territory
    fun checkTerritory(resource: McpResource): TerritoryResult
    fun getTerritoryBoundaries(): List<String>

    // -sec: Security
    fun sanitizeInput(input: String): String
    fun encryptSecret(secret: String): String
    fun decryptSecret(encrypted: String): String
}

data class AuthResult(
    val success: Boolean,
    val token: String? = null,
    val expiresAt: Instant? = null,
    val error: String? = null
)

data class McpResource(
    val id: String,
    val type: String,
    val name: String,
    val properties: Map<String, Any> = emptyMap(),
    val territory: String? = null
)

data class RegistrationInfo(
    val systemId: String,
    val displayName: String,
    val capabilities: List<String>,
    val authRequired: List<String>,
    val version: String
)

data class TerritoryResult(
    val allowed: Boolean,
    val reason: String? = null,
    val boundaries: List<String> = emptyList()
)

/**
 * Base implementation with common functionality.
 */
abstract class BaseMcpIntegration(
    override val systemId: String,
    override val systemName: String,
    protected val configDir: Path
) : McpIntegration {

    protected val authFile = configDir.resolve("$systemId-auth.json")
    protected val territoryFile = configDir.resolve("$systemId-territory.json")
    protected val secretsFile = configDir.resolve("$systemId-secrets.enc")

    init {
        Files.createDirectories(configDir)
    }

    // Default implementations - override in subclasses

    override fun authenticate(credentials: Map<String, String>): AuthResult = AuthResult(false, error = "Not implemented")

    override fun refreshToken(): AuthResult = AuthResult(false, error = "Not implemented")

    override fun revokeAccess(): Boolean = false

    override fun listResources(params: Map<String, String>): List<McpResource> = emptyList()

    override fun getResource(id: String, params: Map<String, String>): McpResource? = null

    override fun createResource(resource: McpResource): McpResource = resource

    override fun updateResource(id: String, updates: Map<String, Any>): McpResource = McpResource(id, "", "")

    override fun deleteResource(id: String): Boolean = false

    override fun register(): RegistrationInfo = RegistrationInfo(systemId, systemName, emptyList(), emptyList(), "1.0")

    override fun discover(): List<RegistrationInfo> = emptyList()

    override fun unregister(): Boolean = false

    override fun checkTerritory(resource: McpResource): TerritoryResult = TerritoryResult(true)

    override fun getTerritoryBoundaries(): List<String> = emptyList()

    override fun sanitizeInput(input: String): String = input.replace("\"", "\\\"")

    override fun encryptSecret(secret: String): String = secret // Placeholder

    override fun decryptSecret(encrypted: String): String = encrypted // Placeholder
}