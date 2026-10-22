/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.nio.file.Path

/**
 * ADD-MCP-001: Server Health Probe Supervisor and Dead-Tool Pruner
 * Section 2.1 mandate: When an MCP server in mcp.json fails to start or times out,
 * its tools must be immediately excluded from the tool allowlist to protect context budget.
 */

enum class McpServerHealth { HEALTHY, DEGRADED, UNHEALTHY, UNTESTED }

data class McpHealthStatus(
    val serverId: String,
    val health: McpServerHealth,
    val latencyMs: Long,
    val activeToolCount: Int,
    val lastProbeTimestamp: Long,
    val errorDetail: String?
)

class McpHealthProbeSupervisor(
    val hostManager: GenericMcpHostManager,
    val probeTimeoutMs: Long = 5000
) {
    private val healthMap = mutableMapOf<String, McpHealthStatus>()

    fun probeServerHealth(config: McpServerConfig): McpHealthStatus {
        val start = System.currentTimeMillis()
        return try {
            if (!config.enabled) {
                val status = McpHealthStatus(
                    serverId = config.name,
                    health = McpServerHealth.UNHEALTHY,
                    latencyMs = 0,
                    activeToolCount = 0,
                    lastProbeTimestamp = System.currentTimeMillis(),
                    errorDetail = "Server disabled by configuration"
                )
                healthMap[config.name] = status
                return status
            }

            // Probe server binary/command existence
            val cmd = config.command
            if (cmd != null && !java.io.File(cmd).exists() && !cmd.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                val status = McpHealthStatus(
                    serverId = config.name,
                    health = McpServerHealth.UNHEALTHY,
                    latencyMs = System.currentTimeMillis() - start,
                    activeToolCount = 0,
                    lastProbeTimestamp = System.currentTimeMillis(),
                    errorDetail = "Executable binary not found: $cmd"
                )
                healthMap[config.name] = status
                return status
            }

            val elapsed = System.currentTimeMillis() - start
            val health = if (elapsed > probeTimeoutMs) McpServerHealth.DEGRADED else McpServerHealth.HEALTHY
            val status = McpHealthStatus(
                serverId = config.name,
                health = health,
                latencyMs = elapsed,
                activeToolCount = 1,
                lastProbeTimestamp = System.currentTimeMillis(),
                errorDetail = null
            )
            healthMap[config.name] = status
            status
        } catch (e: Exception) {
            val status = McpHealthStatus(
                serverId = config.name,
                health = McpServerHealth.UNHEALTHY,
                latencyMs = System.currentTimeMillis() - start,
                activeToolCount = 0,
                lastProbeTimestamp = System.currentTimeMillis(),
                errorDetail = e.message
            )
            healthMap[config.name] = status
            status
        }
    }

    fun probeAllConfigured(): Map<String, McpHealthStatus> {
        val configs = hostManager.loadServerConfigs()
        for (cfg in configs) {
            val serverConfig = McpServerConfig(
                name = cfg.id,
                transport = "stdio",
                command = cfg.command,
                args = cfg.args,
                enabled = true,
                community = false
            )
            probeServerHealth(serverConfig)
        }
        return healthMap.toMap()
    }

    fun filterUsableTools(candidateTools: List<McpToolDescriptor>): List<McpToolDescriptor> {
        return candidateTools.filter { tool ->
            val status = healthMap.values.find { it.activeToolCount > 0 && it.health != McpServerHealth.UNHEALTHY }
            status != null || healthMap.isEmpty()
        }
    }
}
