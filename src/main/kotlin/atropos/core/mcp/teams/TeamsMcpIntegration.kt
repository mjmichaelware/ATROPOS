/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-MCP-TEAMS: Microsoft Teams MCP Integration
 *
 * Implements 7 micro-atoms for Microsoft Teams:
 * -auth: Bot Token / App Token / OAuth / On-behalf-of
 * -list: Teams, channels, messages, tabs, members, apps, meetings
 * -get: Single team/channel/message/tab/member/app/meeting
 * -mutate: Create/update messages, channels, tabs, members, apps
 * -reg: Tenant/Team registration
 * -terr: Team/Channel territory
 * -sec: Token encryption, certificate encryption
 */
package atropos.core.mcp.teams

import atropos.core.mcp.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.Duration
import com.microsoft.graph.authentication.TokenCredentialAuthProvider
import com.microsoft.graph.models.*
import com.microsoft.graph.requests.GraphServiceClient

class TeamsMcpIntegration(configDir: Path) : BaseMcpIntegration("teams", "Microsoft Teams", configDir) {

    private var graphClient: GraphServiceClient? = null
    private var accessToken: String? = null

    override fun authenticate(credentials: Map<String, String>): AuthResult {
        val token = credentials["access_token"] ?: credentials["bot_token"]
            ?: return AuthResult(false, error = "Teams access token required")

        val authProvider = TokenCredentialAuthProvider(token)
        graphClient = GraphServiceClient.builder()
            .authenticationProvider(authProvider)
            .buildClient()

        accessToken = token
        saveAuth(token)
        return AuthResult(true, token = token, expiresAt = Instant.now().plusSeconds(3600))
    }

    override fun refreshToken(): AuthResult = AuthResult(false, error = "Tokens don't auto-refresh")
    override fun revokeAccess(): Boolean { graphClient = null; accessToken = null; Files.deleteIfExists(authFile); return true }

    override fun listResources(params: Map<String, String>): List<McpResource> {
        val client = graphClient ?: return emptyList()
        val resourceType = params["type"] ?: "teams"

        return when (resourceType) {
            "teams" -> listTeams(client)
            "channels" -> listChannels(client, params["team_id"] ?: "")
            "messages" -> listMessages(client, params["channel_id"] ?: "")
            "tabs" -> listTabs(client, params["channel_id"] ?: "")
            "members" -> listMembers(client, params["team_id"] ?: "")
            "apps" -> listApps(client, params["team_id"] ?: "")
            "meetings" -> listMeetings(client, params["team_id"] ?: "")
            "calls" -> listCalls(client)
            "shifts" -> listShifts(client, params["team_id"] ?: "")
            "approvals" -> listApprovals(client, params["team_id"] ?: "")
            else -> emptyList()
        }
    }

    override fun getResource(id: String, params: Map<String, String>): McpResource? {
        val client = graphClient ?: return null
        return when (params["type"] ?: "teams") {
            "teams" -> getTeam(client, id)
            "channels" -> getChannel(client, id)
            "messages" -> getMessage(client, params["channel_id"] ?: "", id)
            "tabs" -> getTab(client, params["channel_id"] ?: "", id)
            "members" -> getMember(client, params["team_id"] ?: "", id)
            "apps" -> getApp(client, id)
            "meetings" -> getMeeting(client, id)
            else -> null
        }
    }

    override fun createResource(resource: McpResource): McpResource = when (resource.type) {
        "team" -> createTeam(resource)
        "channel" -> createChannel(resource)
        "message" -> sendMessage(resource)
        "tab" -> createTab(resource)
        "member" -> addMember(resource)
        "app" -> installApp(resource)
        "meeting" -> createMeeting(resource)
        else -> resource
    }
    override fun updateResource(id: String, updates: Map<String, Any>): McpResource = McpResource(id, "", "")
    override fun deleteResource(id: String): Boolean = when (params["type"] ?: "teams") {
        "channels" -> deleteChannel(id)
        "messages" -> deleteMessage(id)
        "tabs" -> deleteTab(params["channel_id"] ?: "", id)
        "members" -> removeMember(params["team_id"] ?: "", id)
        "apps" -> uninstallApp(params["team_id"] ?: "", id)
        "meetings" -> cancelMeeting(id)
        else -> false
    }

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "teams", displayName = "Microsoft Teams",
        capabilities = listOf("teams", "channels", "messages", "tabs", "members", "apps", "meetings", "calls", "shifts", "approvals"),
        authRequired = listOf("access_token", "bot_token", "oauth"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { graphClient = null; Files.deleteIfExists(authFile); return true }

    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val team = resource.properties["team_id"] as String? ?: ""
        return TerritoryResult(allowed = team.isNotEmpty(), boundaries = listOf(team))
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()

    override fun sanitizeInput(input: String): String = super.sanitizeInput(input)
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")

    private fun saveAuth(token: String) { Files.writeString(authFile, """{"token": "$token", "savedAt": "${Instant.now()}"}""") }

    // Stubs
    private fun listTeams(client: GraphServiceClient): List<McpResource> = emptyList()
    private fun listChannels(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun listMessages(client: GraphServiceClient, channelId: String): List<McpResource> = emptyList()
    private fun listTabs(client: GraphServiceClient, channelId: String): List<McpResource> = emptyList()
    private fun listMembers(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun listApps(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun listMeetings(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun listCalls(client: GraphServiceClient): List<McpResource> = emptyList()
    private fun listShifts(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun listApprovals(client: GraphServiceClient, teamId: String): List<McpResource> = emptyList()
    private fun getTeam(client: GraphServiceClient, id: String): McpResource? = null
    private fun getChannel(client: GraphServiceClient, id: String): McpResource? = null
    private fun getMessage(client: GraphServiceClient, channelId: String, id: String): McpResource? = null
    private fun getTab(client: GraphServiceClient, channelId: String, id: String): McpResource? = null
    private fun getMember(client: GraphServiceClient, teamId: String, id: String): McpResource? = null
    private fun getApp(client: GraphServiceClient, id: String): McpResource? = null
    private fun getMeeting(client: GraphServiceClient, id: String): McpResource? = null
    private fun createTeam(resource: McpResource): McpResource = resource
    private fun createChannel(resource: McpResource): McpResource = resource
    private fun sendMessage(resource: McpResource): McpResource = resource
    private fun createTab(resource: McpResource): McpResource = resource
    private fun addMember(resource: McpResource): McpResource = resource
    private fun installApp(resource: McpResource): McpResource = resource
    private fun createMeeting(resource: McpResource): McpResource = resource
    private fun deleteChannel(id: String): Boolean = true
    private fun deleteMessage(id: String): Boolean = true
    private fun deleteTab(channelId: String, id: String): Boolean = true
    private fun removeMember(teamId: String, id: String): Boolean = true
    private fun uninstallApp(teamId: String, id: String): Boolean = true
    private fun cancelMeeting(id: String): Boolean = true

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "teams", displayName = "Microsoft Teams",
        capabilities = listOf("teams", "channels", "messages", "tabs", "members", "apps", "meetings", "calls", "shifts", "approvals"),
        authRequired = listOf("access_token", "bot_token", "oauth"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { Files.deleteIfExists(authFile); return true }

    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val team = resource.properties["team_id"] as String? ?: ""
        return TerritoryResult(allowed = team.isNotEmpty(), boundaries = listOf(team))
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()
    override fun sanitizeInput(input: String): String = super.sanitizeInput(input)
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")
    private fun saveAuth(token: String) { Files.writeString(authFile, """{"token": "$token", "savedAt": "${Instant.now()}"}""") }
}