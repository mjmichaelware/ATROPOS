/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.TypedToolExecutor
import atropos.core.security.DefaultSecretSource
import atropos.core.security.RedactionFilter
import atropos.core.security.SecretSource

enum class McpHealth { HEALTHY, UNHEALTHY, UNTESTED }

data class McpServerConfig(
    val name: String,
    val transport: String,
    val command: String?,
    val args: List<String>,
    val enabled: Boolean,
    val community: Boolean,
    val url: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap()
) {
    val remote: Boolean get() = transport.lowercase() in setOf("http", "sse", "streamable-http")
}

data class McpServerStatus(val server: McpServerConfig, val health: McpHealth, val reason: String)

data class McpToolBudget(val maxTools: Int = 32, val maxDescriptionChars: Int = 4_000) {
    init { require(maxTools > 0 && maxDescriptionChars > 0) }
}

data class McpToolDescriptor(val name: String, val description: String = "")

data class McpEvidenceRef(val sha256: String?, val path: Path?, val noEvidenceReason: String?)
data class McpToolCallResult(val response: String, val evidence: McpEvidenceRef)

/** Local MCP configuration and health owner; execution still crosses InboundToolBridge. */
class McpHostManager(
    private val root: Path,
    private val localOnly: Boolean = true,
    private val allowlist: Set<String> = emptySet(),
    private val probe: ((McpServerConfig) -> McpHealth)? = null,
    private val territoryBridge: McpTerritoryBridge = McpTerritoryBridge(
        setOf("inspect", "verify", "convert_to_markdown"),
        gate = { proposal ->
            val policyProposal = if (proposal.actor.identity == "mcp:mcp-cli") {
                proposal.copy(actor = ActionActor.HumanOwner)
            } else {
                proposal
            }
            BoundedAgencyGate().evaluate(policyProposal)
        }
    ),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    remoteRequest: ((McpServerConfig, String) -> String)? = null,
    private val toolExecutor: TypedToolExecutor = TypedToolExecutor(),
    private val secretSource: SecretSource = DefaultSecretSource.create()
) {
    private val configPath = root.resolve("mcp.json").normalize()
    private val evidenceRoot = root.resolve(".atropos/mcp/evidence").normalize()
    private val healthPath = root.resolve(".atropos/mcp/health.tsv").normalize()
    private val redactionFilter = RedactionFilter()
    private val transportDriver = McpTransportDriver(root, processRunner, territoryBridge, remoteRequest)

    fun load(): List<McpServerConfig> {
        IntegrationRegistry.requireRegistered("mcp")
        if (!Files.isRegularFile(configPath)) return emptyList()
        return McpConfigParser.parse(Files.readString(configPath))
    }

    fun statuses(): List<McpServerStatus> {
        val statuses = load().map { server ->
            when {
                server.name !in allowlist && allowlist.isNotEmpty() -> McpServerStatus(server, McpHealth.UNTESTED, "not in allowlist")
                !server.enabled -> McpServerStatus(server, McpHealth.UNTESTED, "disabled by default")
                server.community && server.name !in allowlist -> McpServerStatus(server, McpHealth.UNTESTED, "community server requires explicit allowlist")
                localOnly && server.remote -> McpServerStatus(server, McpHealth.UNTESTED, "remote MCP disabled by localOnly")
                !supportedTransport(server.transport) -> McpServerStatus(server, McpHealth.UNTESTED, "unsupported MCP transport: ${server.transport}")
                else -> runCatching { McpServerStatus(server, (probe ?: ::defaultProbe)(server), "init + tools/list probe") }
                    .getOrElse { McpServerStatus(server, McpHealth.UNHEALTHY, "probe failed: ${it.javaClass.simpleName}") }
            }
        }
        persistHealth(statuses)
        return statuses
    }

    fun search(query: String): List<McpServerConfig> {
        require(query.isNotBlank()) { "MCP search query is required" }
        val gate = territoryBridge.judge(
            InboundToolRequest(
                source = InboundSource.MCP,
                callerId = "mcp-cli",
                operation = "inspect",
                paths = listOf(".")
            )
        )
        when (gate) {
            is InboundGateResult.Refused -> error("MCP search refused by territory bridge: ${gate.reason}")
            is InboundGateResult.Judged -> require(gate.decision.disposition == AgencyDisposition.ALLOWED) {
                "MCP search refused by policy: ${gate.decision.reason}"
            }
        }
        val needle = query.trim().lowercase()
        return load().asSequence()
            .filter { server ->
                (server.name.lowercase().contains(needle) || server.transport.lowercase().contains(needle)) &&
                    !(localOnly && server.remote) &&
                    (allowlist.isEmpty() && !server.community || server.name in allowlist)
            }
            .toList()
    }

    private fun persistHealth(statuses: List<McpServerStatus>) {
        val absoluteRoot = root.toAbsolutePath().normalize()
        require(healthPath.toAbsolutePath().normalize().startsWith(absoluteRoot)) {
            "MCP health state escaped repository"
        }
        Files.createDirectories(healthPath.parent)
        val body = buildString {
            statuses.forEach { status ->
                append(status.server.name.replace('\t', '_').replace('\n', '_'))
                append('\t').append(status.health.name)
                append('\t').append(status.reason.replace('\t', ' ').replace('\n', ' '))
                append('\n')
            }
        }
        Files.writeString(healthPath, body)
    }

    fun boundedTools(tools: List<McpToolDescriptor>, budget: McpToolBudget = McpToolBudget()): List<McpToolDescriptor> =
        tools.asSequence().take(budget.maxTools).map { it.copy(description = it.description.take(budget.maxDescriptionChars)) }.toList()

    fun budgetSummary(): String {
        val budget = McpToolBudget()
        return "max_tools=${budget.maxTools} max_description_chars=${budget.maxDescriptionChars}"
    }

    fun callTool(
        serverName: String,
        toolName: String,
        argumentsJson: String = "{}",
        maxResponseBytes: Int = 128 * 1024,
        callerId: String = "mcp-cli",
        operation: String = toolName,
        territoryPaths: List<String> = listOf("."),
        toolBudget: McpToolBudget = McpToolBudget()
    ): McpToolCallResult {
        require(serverName.isNotBlank() && toolName.isNotBlank()) { "MCP server and tool are required" }
        val boundedArguments = McpConfigParser.requireJsonObject(argumentsJson)
        require(boundedArguments.length <= 32 * 1024) { "MCP tool arguments exceed the bounded request size" }
        require(maxResponseBytes in 1..1024 * 1024) { "MCP response limit is outside the bounded range" }
        val gate = territoryBridge.judge(
            InboundToolRequest(
                source = InboundSource.MCP,
                callerId = callerId,
                operation = operation,
                paths = territoryPaths
            )
        )
        val judged = when (gate) {
            is InboundGateResult.Refused -> error("MCP tool refused by territory bridge: ${gate.reason}")
            is InboundGateResult.Judged -> gate
        }
        require(judged.decision.disposition == AgencyDisposition.ALLOWED) {
            "MCP tool refused by policy: ${judged.decision.reason}"
        }
        val server = load().firstOrNull { it.name == serverName }
            ?: error("MCP server is not configured: $serverName")
        require(supportedTransport(server.transport)) { "unsupported MCP transport: ${server.transport}" }
        require(!isMemoryAuthorityMutation(server, operation, territoryPaths)) {
            "memory MCP cannot write SourceAuthority or governance ledger paths"
        }
        require(server.enabled) { "MCP server is disabled: $serverName" }
        require(server.name in allowlist || (!server.community && allowlist.isEmpty())) {
            "MCP server is not allowlisted: $serverName"
        }
        require(!localOnly || !server.remote) { "remote MCP disabled by localOnly" }
        val health = statuses().firstOrNull { it.server.name == serverName }
            ?: error("MCP server status is unavailable: $serverName")
        require(health.health == McpHealth.HEALTHY) {
            "MCP server is not healthy: $serverName (${health.reason})"
        }
        val execution = toolExecutor.execute(judged.decision) {
            if (server.remote) {
                transportDriver.remoteCall(server, toolName, boundedArguments, maxResponseBytes, toolBudget, runtimeHeaders(server), ::requireToolWithinBudget)
            } else {
                val command = server.command ?: error("MCP server has no stdio command: $serverName")
                val process = processRunner.start(
                    listOf(command) + server.args,
                    root,
                    environment = runtimeEnvironment(server)
                )
                val worker = Executors.newSingleThreadExecutor()
                try {
                    val response = worker.submit<String> {
                        transportDriver.exchangeStdio(process, toolName, boundedArguments, maxResponseBytes, toolBudget, ::requireToolWithinBudget)
                    }.get(5, TimeUnit.SECONDS)
                    require(response.contains("\"id\":3")) { "MCP tools/call returned no response" }
                    response
                } finally {
                    process.destroyForcibly()
                    process.waitFor(1, TimeUnit.SECONDS)
                    worker.shutdownNow()
                }
            }
        }
        require(execution.executed) {
            execution.refusalReason ?: "MCP tool execution was not authorized"
        }
        val rawResponse = execution.output
            ?: error("MCP tool execution returned no response")
        val safeResponse = redactionFilter.redact(rawResponse)
        return McpToolCallResult(safeResponse, recordToolResult(serverName, toolName, safeResponse))
    }

    fun recordToolResult(server: String, tool: String, result: String, evidenceAvailable: Boolean = true): McpEvidenceRef {
        if (!evidenceAvailable) return McpEvidenceRef(null, null, "tool result declared no durable evidence")
        val safeResult = redactionFilter.redact(result)
        val hash = sha256("$server\n$tool\n$safeResult")
        require(evidenceRoot.startsWith(root.toAbsolutePath().normalize())) { "MCP evidence escaped repository" }
        Files.createDirectories(evidenceRoot)
        val path = evidenceRoot.resolve("$hash.txt")
        val temp = Files.createTempFile(evidenceRoot, "mcp-", ".tmp")
        Files.writeString(temp, safeResult)
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
        return McpEvidenceRef(hash, path, null)
    }

    private fun requireToolWithinBudget(response: String, toolName: String, budget: McpToolBudget) {
        val toolsBody = Regex("\\\"tools\\\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(response)?.groupValues?.getOrNull(1).orEmpty()
        val descriptors = Regex("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .findAll(toolsBody)
            .map { McpToolDescriptor(it.groupValues[1]) }
            .toList()
        require(descriptors.any { it.name == toolName && boundedTools(descriptors, budget).any { bounded -> bounded.name == toolName } }) {
            "MCP tool '$toolName' is not advertised within the configured tool budget"
        }
    }

    private fun isMemoryAuthorityMutation(
        server: McpServerConfig,
        operation: String,
        paths: List<String>
    ): Boolean {
        val memoryServer = server.name.lowercase().contains("memory")
        val mutation = operation.lowercase() in setOf("write", "append", "update", "replace", "delete", "create", "set")
        val authorityPath = paths.any { path ->
            val normalized = path.lowercase().replace('\\', '/')
            normalized.contains("sourceauthority") || normalized.contains("source-authority") ||
                normalized.contains("governance") || normalized.contains("ledger")
        }
        return memoryServer && mutation && authorityPath
    }

    private fun supportedTransport(transport: String): Boolean =
        transport.lowercase() in setOf("stdio", "http", "sse", "streamable-http")

    private fun runtimeEnvironment(server: McpServerConfig): Map<String, String> =
        resolveSecretSafeMap(server.environment)

    private fun runtimeHeaders(server: McpServerConfig): Map<String, String> =
        resolveSecretSafeMap(server.headers)

    private fun resolveSecretSafeMap(values: Map<String, String>): Map<String, String> =
        values.mapValues { (name, value) ->
            val reference = ENV_REFERENCE.matchEntire(value)?.groupValues?.getOrNull(1)
            if (reference != null) {
                secretSource.lookup(reference).value
                    ?: error("MCP environment reference is not configured: $reference")
            } else {
                require(!SECRET_ENV_NAME.containsMatchIn(name)) {
                    "MCP secret environment '$name' must use an environment or vault reference"
                }
                value
            }
        }

    private fun defaultProbe(server: McpServerConfig): McpHealth =
        transportDriver.defaultProbe(server, runtimeEnvironment(server), runtimeHeaders(server))

    private companion object {
        val ENV_REFERENCE = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}")
        val SECRET_ENV_NAME = Regex("(?i)(key|token|secret|password|credential|authorization)")

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
