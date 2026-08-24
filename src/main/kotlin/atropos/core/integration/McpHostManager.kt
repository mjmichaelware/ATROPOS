package atropos.core.integration

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter

enum class McpHealth { HEALTHY, UNHEALTHY, UNTESTED }

data class McpServerConfig(
    val name: String,
    val transport: String,
    val command: String?,
    val args: List<String>,
    val enabled: Boolean,
    val community: Boolean,
    val url: String? = null
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
    /**
     * The CLI is an explicitly local operator surface, so its root territory
     * is the human owner's. Any other caller remains a hierarchy node and
     * must already hold a delegated grant. Both branches still use the same
     * inbound bridge and bounded policy gate.
     */
    private val territoryBridge: McpTerritoryBridge = McpTerritoryBridge(
        // MarkItDown is a bounded local read/ingest operation.  It produces
        // an attested markdown artifact; it is not an arbitrary MCP write.
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
    private val remoteRequest: ((McpServerConfig, String) -> String)? = null
) {
    private val configPath = root.resolve("mcp.json").normalize()
    private val evidenceRoot = root.resolve(".atropos/mcp/evidence").normalize()
    private val healthPath = root.resolve(".atropos/mcp/health.tsv").normalize()
    private val redactionFilter = RedactionFilter()

    fun load(): List<McpServerConfig> {
        if (!Files.isRegularFile(configPath)) return emptyList()
        val text = Files.readString(configPath)
        val serversBody = Regex("\\\"servers\\\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        return Regex("\\{(.*?)}", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(serversBody).mapNotNull { match ->
            val body = match.groupValues[1]
            fun field(name: String): String? = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(body)?.groupValues?.get(1)
            fun bool(name: String, default: Boolean): Boolean =
                Regex("\\\"$name\\\"\\s*:\\s*(true|false)").find(body)?.groupValues?.get(1)?.toBoolean() ?: default
            val name = field("name") ?: return@mapNotNull null
            val args = Regex("\\\"args\\\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL)).find(body)
                ?.groupValues?.get(1)?.let { values -> Regex("\\\"([^\\\"]*)\\\"").findAll(values).map { it.groupValues[1] }.toList() }
                .orEmpty()
            McpServerConfig(name, field("transport") ?: "stdio", field("command"), args, bool("enabled", false), bool("community", true), field("url"))
        }.toList()
    }

    fun statuses(): List<McpServerStatus> {
        val statuses = load().map { server ->
            when {
            server.name !in allowlist && allowlist.isNotEmpty() -> McpServerStatus(server, McpHealth.UNTESTED, "not in allowlist")
            !server.enabled -> McpServerStatus(server, McpHealth.UNTESTED, "disabled by default")
            server.community && server.name !in allowlist -> McpServerStatus(server, McpHealth.UNTESTED, "community server requires explicit allowlist")
            localOnly && server.remote -> McpServerStatus(server, McpHealth.UNTESTED, "remote MCP disabled by localOnly")
            else -> runCatching { McpServerStatus(server, (probe ?: ::defaultProbe)(server), "init + tools/list probe") }
                .getOrElse { McpServerStatus(server, McpHealth.UNHEALTHY, "probe failed: ${it.javaClass.simpleName}") }
            }
        }
        persistHealth(statuses)
        return statuses
    }

    /**
     * Searches the configured MCP catalog only. This is deliberately not a
     * registry client: search cannot download, install, enable, or probe a
     * server. Local-only mode also hides remote candidates from the result.
     */
    fun search(query: String): List<McpServerConfig> {
        require(query.isNotBlank()) { "MCP search query is required" }
        val needle = query.trim().lowercase()
        return load().asSequence()
            .filter { server ->
                (server.name.lowercase().contains(needle) || server.transport.lowercase().contains(needle)) &&
                    !(localOnly && server.remote) &&
                    (allowlist.isEmpty() && !server.community || server.name in allowlist)
            }
            .toList()
    }

    /** Persists labels and probe state only; command arguments and secrets never enter this file. */
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

    /** Executes one allowlisted local stdio tool and persists its result evidence. */
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
        require(argumentsJson.length <= 32 * 1024) { "MCP tool arguments exceed the bounded request size" }
        require(maxResponseBytes in 1..1024 * 1024) { "MCP response limit is outside the bounded range" }
        val gate = territoryBridge.judge(
            InboundToolRequest(
                source = InboundSource.MCP,
                callerId = callerId,
                operation = operation,
                paths = territoryPaths
            )
        )
        when (gate) {
            is InboundGateResult.Refused -> error("MCP tool refused by territory bridge: ${gate.reason}")
            is InboundGateResult.Judged -> require(gate.decision.disposition == AgencyDisposition.ALLOWED) {
                "MCP tool refused by policy: ${gate.decision.reason}"
            }
        }
        val server = load().firstOrNull { it.name == serverName }
            ?: error("MCP server is not configured: $serverName")
        require(server.enabled) { "MCP server is disabled: $serverName" }
        require(server.name in allowlist || (!server.community && allowlist.isEmpty())) {
            "MCP server is not allowlisted: $serverName"
        }
        require(!localOnly || !server.remote) { "remote MCP disabled by localOnly" }
        if (server.remote) {
            val response = remoteCall(server, toolName, argumentsJson, maxResponseBytes, toolBudget)
            val safeResponse = redactionFilter.redact(response)
            return McpToolCallResult(safeResponse, recordToolResult(serverName, toolName, safeResponse))
        }
        val command = server.command ?: error("MCP server has no stdio command: $serverName")
        val process = processRunner.start(listOf(command) + server.args, root)
        process.outputStream.bufferedWriter().use { writer ->
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n")
            writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n")
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n")
            val escapedTool = toolName.replace("\\", "\\\\").replace("\"", "\\\"")
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"$escapedTool\",\"arguments\":$argumentsJson}}\n")
            writer.flush()
        }
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val response = executor.submit<String> {
                process.inputStream.bufferedReader().use { reader ->
                    val output = StringBuilder()
                    while (output.length < maxResponseBytes) {
                        val line = reader.readLine() ?: break
                        output.append(line).append('\n')
                        if (line.contains("\"id\":3")) break
                    }
                    output.toString().take(maxResponseBytes)
                }
            }.get(5, TimeUnit.SECONDS)
            require(response.contains("\"id\":3")) { "MCP tools/call returned no response" }
            requireToolWithinBudget(response, toolName, toolBudget)
            val safeResponse = redactionFilter.redact(response)
            McpToolCallResult(safeResponse, recordToolResult(serverName, toolName, safeResponse))
        } finally {
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
            executor.shutdownNow()
        }
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

    private fun remoteCall(
        server: McpServerConfig,
        toolName: String,
        argumentsJson: String,
        maxResponseBytes: Int,
        toolBudget: McpToolBudget
    ): String {
        val initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
        require(remoteExchange(server, initialize).contains("\"id\":1")) { "MCP HTTP initialize returned no response" }
        val toolsList = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        val toolsListResponse = remoteExchange(server, toolsList)
        require(toolsListResponse.contains("\"id\":2")) { "MCP HTTP tools/list returned no response" }
        requireToolWithinBudget(toolsListResponse, toolName, toolBudget)
        val request = buildString {
            append("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{")
            append("\"name\":\"").append(jsonEscape(toolName)).append("\",")
            append("\"arguments\":").append(argumentsJson).append("}}")
        }
        val response = remoteExchange(server, request)
        val bounded = response.take(maxResponseBytes)
        require(bounded.contains("\"id\":3")) { "MCP HTTP tools/call returned no response" }
        return bounded
    }

    /** Applies the single MCP injection budget before any tools/call is sent. */
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

    private fun defaultProbe(server: McpServerConfig): McpHealth =
        if (server.remote) {
            val initialize = remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}")
            val toolsList = remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}")
            if (initialize.contains("\"id\":1") && toolsList.contains("\"id\":2")) McpHealth.HEALTHY else McpHealth.UNHEALTHY
        } else {
            probeProcess(server, root)
        }

    private fun remoteExchange(server: McpServerConfig, body: String): String =
        remoteRequest?.invoke(server, body) ?: postRemote(server, body)

    private fun postRemote(server: McpServerConfig, body: String): String {
        val url = server.url?.trim()?.takeIf { it.isNotBlank() }
            ?: error("MCP remote server has no url: ${server.name}")
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "MCP remote url must use http or https: ${server.name}"
        }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "MCP HTTP request failed status=${response.statusCode()} server=${server.name}"
        }
        return response.body()
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private companion object {
        fun probeProcess(server: McpServerConfig, root: Path): McpHealth {
            val command = server.command ?: return McpHealth.UNHEALTHY
            val process = BoundedProcessRunner().start(listOf(command) + server.args, root)
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n")
                writer.write("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n")
                writer.flush()
            }
            // MCP stdio servers normally stay alive after the handshake. Waiting
            // for process exit therefore classified a responsive server as dead.
            // Read a bounded response window instead, then always reap the probe.
            val reader = process.inputStream.bufferedReader()
            val executor = Executors.newSingleThreadExecutor()
            return try {
                val response = executor.submit<List<String>> {
                    val lines = mutableListOf<String>()
                    while (lines.size < 32) {
                        val line = reader.readLine() ?: break
                        lines += line
                        if (lines.any { it.contains("\"id\":1") } && lines.any { it.contains("\"id\":2") }) break
                    }
                    lines
                }.get(3, TimeUnit.SECONDS)
                if (response.any { it.contains("\"id\":1") } && response.any { it.contains("\"id\":2") }) {
                    McpHealth.HEALTHY
                } else {
                    McpHealth.UNHEALTHY
                }
            } catch (_: Exception) {
                McpHealth.UNHEALTHY
            } finally {
                reader.close()
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                executor.shutdownNow()
            }
        }

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
