/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * MCP Integration Registry - Main entry point
 *
 * Registers all available MCP integrations.
 */
package atropos.core.mcp

import java.nio.file.Files
import java.nio.file.Path

class McpIntegrationRegistry(configDir: Path) {

    private val integrations = mutableMapOf<String, McpIntegration>()
    private val configDir = configDir.resolve("mcp").apply { Files.createDirectories(this) }

    fun register(integration: McpIntegration): Boolean {
        return integrations.putIfAbsent(integration.systemId, integration) == null
    }

    fun get(systemId: String): McpIntegration? = integrations[systemId]

    fun getAll(): List<McpIntegration> = integrations.values.toList()

    fun unregister(systemId: String): Boolean = integrations.remove(systemId) != null

    fun initializeAll() {
        // Register all available integrations
        // register(GitLabMcpIntegration(configDir.resolve("gitlab")))
        // register(GitHubMcpIntegration(configDir.resolve("github")))
        // register(JiraMcpIntegration(configDir.resolve("jira")))
        // register(LinearMcpIntegration(configDir.resolve("linear")))
        // register(DockerMcpIntegration(configDir.resolve("docker")))
        // register(PostgresMcpIntegration(configDir.resolve("postgres")))
        // register(BitbucketMcpIntegration(configDir.resolve("bitbucket")))
        // register(ConfluenceMcpIntegration(configDir.resolve("confluence")))
        // register(RedisMcpIntegration(configDir.resolve("redis")))
        // register(SlackMcpIntegration(configDir.resolve("slack")))
        // register(AwsMcpIntegration(configDir.resolve("aws")))
        // register(GcpMcpIntegration(configDir.resolve("gcp")))
        // register(AzureMcpIntegration(configDir.resolve("azure")))
        // register(AzureDevOpsMcpIntegration(configDir.resolve("azuredevops")))
        // register(BitbucketMcpIntegration(configDir.resolve("bitbucket")))
        // register(ConfluenceMcpIntegration(configDir.resolve("confluence")))
        // register(RedisMcpIntegration(configDir.resolve("redis")))
        // register(SlackMcpIntegration(configDir.resolve("slack")))
        // register(AwsMcpIntegration(configDir.resolve("aws")))
        // register(GcpMcpIntegration(configDir.resolve("gcp")))
        // register(AzureMcpIntegration(configDir.resolve("azure")))
        // register(AzureDevOpsMcpIntegration(configDir.resolve("azuredevops")))
        // register(DatadogMcpIntegration(configDir.resolve("datadog")))
        // register(NewRelicMcpIntegration(configDir.resolve("newrelic")))
        // register(SonarQubeMcpIntegration(configDir.resolve("sonarqube")))
        // register(SnykMcpIntegration(configDir.resolve("snyk")))
        // register(TerraformMcpIntegration(configDir.resolve("terraform")))
        // register(PulumiMcpIntegration(configDir.resolve("pulumi")))
        // register(KubernetesMcpIntegration(configDir.resolve("kubernetes")))
        // register(SupabaseMcpIntegration(configDir.resolve("supabase")))
        // register(FirebaseMcpIntegration(configDir.resolve("firebase")))
        // register(PagerDutyMcpIntegration(configDir.resolve("pagerduty")))
        // register(OpsgenieMcpIntegration(configDir.resolve("opsgenie")))
        // register(AsanaMcpIntegration(configDir.resolve("asana")))
        // register(ClickUpMcpIntegration(configDir.resolve("clickup")))
        // register(NotionMcpIntegration(configDir.resolve("notion")))
        // register(DiscordMcpIntegration(configDir.resolve("discord")))
        // register(TeamsMcpIntegration(configDir.resolve("teams")))
        // register(PlaywrightMcpIntegration(configDir.resolve("playwright")))
        // register(PuppeteerMcpIntegration(configDir.resolve("puppeteer")))
        // register(SentryMcpIntegration(configDir.resolve("sentry")))
    }

    fun discoverAll(): List<RegistrationInfo> {
        return integrations.values.map { it.register() }
    }

    fun findByCapability(capability: String): List<McpIntegration> {
        return integrations.values.filter { capability in it.register().capabilities }
    }

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

    fun listIntegrations(): String {
        return integrations.values.map { i ->
            val reg = i.register()
            "${reg.systemId} (${reg.displayName}): ${reg.capabilities.joinToString(", ")}"
        }.joinToString("\n")
    }
}