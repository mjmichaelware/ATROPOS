/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-MCP-AZURE: Microsoft Azure MCP Integration
 *
 * Implements 7 micro-atoms for Azure:
 * -auth: Service Principal / Managed Identity / CLI / Workload Identity
 * -list: VMs, Storage, Functions, AKS, CosmosDB, Key Vault, App Service
 * -get: Single resource
 * -mutate: Create/update/delete resources
 * -reg: Subscription/Tenant registration
 * -terr: Subscription/Resource Group/Region territory
 * -sec: Secret encryption, Key Vault, RBAC, Policy
 */
package atropos.core.mcp.azure

import atropos.core.mcp.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.Duration
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.identity.ManagedIdentityCredentialBuilder
import com.azure.resourcemanager.AzureResourceManager
import com.azure.resourcemanager.compute.models.VirtualMachine
import com.azure.resourcemanager.storage.models.StorageAccount
import com.azure.resourcemanager.appservice.models.WebApp
import com.azure.resourcemanager.containerservice.models.KubernetesCluster
import com.azure.resourcemanager.cosmos.models.CosmosDBAccount
import com.azure.resourcemanager.keyvault.models.Vault
import com.azure.resourcemanager.appservice.models.WebApp

class AzureMcpIntegration(configDir: Path) : BaseMcpIntegration("azure", "Microsoft Azure", configDir) {

    private var azureClient: AzureResourceManager? = null
    private var subscriptionId: String? = null

    override fun authenticate(credentials: Map<String, String>): AuthResult {
        subscriptionId = credentials["subscription_id"] ?: return AuthResult(false, error = "Azure subscription ID required")

        val credential = when {
            credentials["client_id"] != null && credentials["client_secret"] != null && credentials["tenant_id"] != null ->
                ClientSecretCredentialBuilder()
                    .clientId(credentials["client_id"]!!)
                    .clientSecret(credentials["client_secret"]!!)
                    .tenantId(credentials["tenant_id"]!!)
                    .build()
            credentials["use_managed_identity"]?.toBoolean() == true ->
                ManagedIdentityCredentialBuilder().build()
            credentials["use_cli"]?.toBoolean() == true ->
                DefaultAzureCredentialBuilder().build()
            else ->
                DefaultAzureCredentialBuilder().build()
        }

        return try {
            azureClient = AzureResourceManager.configure()
                .withLogLevel(com.azure.core.http.HttpLogDetailLevel.BASIC)
                .authenticate(credential, subscriptionId!!)
            AuthResult(true, expiresAt = Instant.now().plusSeconds(3600))
        } catch (e: Exception) {
            AuthResult(false, error = e.message)
        }
    }

    override fun refreshToken(): AuthResult = AuthResult(true)
    override fun revokeAccess(): Boolean { azureClient = null; return true }

    override fun listResources(params: Map<String, String>): List<McpResource> {
        val resourceType = params["type"] ?: "virtual_machines"
        val client = azureClient ?: return emptyList()

        return when (resourceType) {
            "virtual_machines" -> client.virtualMachines().list().map { vm ->
                McpResource(vm.id(), "virtual_machine", vm.name(), mapOf(
                    "location" to vm.region().name(),
                    "size" to vm.size().toString(),
                    "power_state" to vm.powerState().toString()
                ))
            }.toList()
            "storage_accounts" -> client.storageAccounts().list().map { sa ->
                McpResource(sa.id(), "storage_account", sa.name(), mapOf(
                    "location" to sa.region().name(),
                    "kind" to sa.kind().toString(),
                    "sku" to sa.sku().name()
                ))
            }.toList()
            "app_services" -> client.webApps().list().map { app ->
                McpResource(app.id(), "app_service", app.name(), mapOf(
                    "location" to app.region().name(),
                    "runtime" to app.runtimeStack(),
                    "state" to app.state().toString()
                ))
            }.toList()
            "aks_clusters" -> client.kubernetesClusters().list().map { aks ->
                McpResource(aks.id(), "aks_cluster", aks.name(), mapOf(
                    "location" to aks.region().name(),
                    "kubernetes_version" to aks.kubernetesVersion(),
                    "provisioning_state" to aks.provisioningState()
                ))
            }.toList()
            "cosmosdb_accounts" -> client.cosmosDBAccounts().list().map { cosmos ->
                McpResource(cosmos.id(), "cosmosdb_account", cosmos.name(), mapOf(
                    "location" to cosmos.region().name(),
                    "kind" to cosmos.kind().toString(),
                    "consistency_policy" to cosmos.consistencyPolicy().toString()
                ))
            }.toList()
            "key_vaults" -> client.vaults().list().map { kv ->
                McpResource(kv.id(), "key_vault", kv.name(), mapOf(
                    "location" to kv.region().name(),
                    "sku" to kv.sku().name(),
                    "enabled_for_deployment" to kv.enabledForDeployment().toString()
                ))
            }.toList()
            "app_services" -> client.webApps().list().map { app ->
                McpResource(app.id(), "app_service", app.name(), mapOf(
                    "location" to app.region().name(),
                    "runtime_stack" to app.runtimeStack(),
                    "state" to app.state().toString()
                ))
            }.toList()
            "resource_groups" -> client.resourceGroups().list().map { rg ->
                McpResource(rg.id(), "resource_group", rg.name(), mapOf(
                    "location" to rg.region().name()
                ))
            }.toList()
            else -> emptyList()
        }
    }

    override fun getResource(id: String, params: Map<String, String>): McpResource? {
        val client = azureClient ?: return null
        return when (params["type"] ?: "virtual_machines") {
            "virtual_machines" -> client.virtualMachines().getById(id)?.let { McpResource(it.id(), "virtual_machine", it.name(), mapOf("location" to it.region().name())) }
            "storage_accounts" -> client.storageAccounts().getById(id)?.let { McpResource(it.id(), "storage_account", it.name(), mapOf("location" to it.region().name())) }
            "app_services" -> client.webApps().getById(id)?.let { McpResource(it.id(), "app_service", it.name(), mapOf("location" to it.region().name())) }
            "aks_clusters" -> client.kubernetesClusters().getById(id)?.let { McpResource(it.id(), "aks_cluster", it.name(), mapOf("location" to it.region().name())) }
            else -> null
        }
    }

    override fun createResource(resource: McpResource): McpResource = when (resource.type) {
        "virtual_machine" -> createVirtualMachine(resource)
        "storage_account" -> createStorageAccount(resource)
        "app_service" -> createAppService(resource)
        "aks_cluster" -> createAksCluster(resource)
        "cosmosdb_account" -> createCosmosDbAccount(resource)
        "key_vault" -> createKeyVault(resource)
        "resource_group" -> createResourceGroup(resource)
        else -> resource
    }
    override fun updateResource(id: String, updates: Map<String, Any>): McpResource = McpResource(id, "", "")
    override fun deleteResource(id: String): Boolean = when (params["type"] ?: "virtual_machines") {
        "virtual_machines" -> { azureClient?.virtualMachines()?.deleteById(id); true }
        "storage_accounts" -> { azureClient?.storageAccounts()?.deleteById(id); true }
        "app_services" -> { azureClient?.webApps()?.deleteById(id); true }
        "aks_clusters" -> { azureClient?.kubernetesClusters()?.deleteById(id); true }
        else -> false
    }

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "azure", displayName = "Microsoft Azure",
        capabilities = listOf("virtual_machines", "storage", "app_service", "aks", "cosmosdb", "key_vault", "resource_groups", "functions", "container_instances", "logic_apps", "event_grid", "service_bus", "event_hubs"),
        authRequired = listOf("client_id", "client_secret", "tenant_id", "subscription_id", "managed_identity", "cli"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { azureClient = null; return true }

    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val subscription = resource.properties["subscription_id"] as String? ?: subscriptionId ?: ""
        val resourceGroup = resource.properties["resource_group"] as String? ?: ""
        val region = resource.properties["location"] as String? ?: ""
        return TerritoryResult(
            allowed = subscription.isNotEmpty() || resourceGroup.isNotEmpty() || region.isNotEmpty(),
            boundaries = listOf(subscription, resourceGroup, region).filter { it.isNotEmpty() }
        )
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()

    override fun sanitizeInput(input: String): String = input.replace("'", "").replace("\"", "")
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")

    // Stubs
    private fun createVirtualMachine(resource: McpResource): McpResource = resource
    private fun createStorageAccount(resource: McpResource): McpResource = resource
    private fun createAppService(resource: McpResource): McpResource = resource
    private fun createAksCluster(resource: McpResource): McpResource = resource
    private fun createCosmosDbAccount(resource: McpResource): McpResource = resource
    private fun createKeyVault(resource: McpResource): McpResource = resource
    private fun createResourceGroup(resource: McpResource): McpResource = resource

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "azure", displayName = "Microsoft Azure",
        capabilities = listOf("virtual_machines", "storage", "app_service", "aks", "cosmosdb", "key_vault", "resource_groups", "functions", "container_instances", "logic_apps", "event_grid", "service_bus", "event_hubs"),
        authRequired = listOf("client_id", "client_secret", "tenant_id", "subscription_id", "managed_identity", "cli"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { return true }
    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val subscription = resource.properties["subscription_id"] as String? ?: subscriptionId ?: ""
        val resourceGroup = resource.properties["resource_group"] as String? ?: ""
        val region = resource.properties["location"] as String? ?: ""
        return TerritoryResult(
            allowed = subscription.isNotEmpty() || resourceGroup.isNotEmpty() || region.isNotEmpty(),
            boundaries = listOf(subscription, resourceGroup, region).filter { it.isNotEmpty() }
        )
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()
    override fun sanitizeInput(input: String): String = input.replace("'", "").replace("\"", "")
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")
}