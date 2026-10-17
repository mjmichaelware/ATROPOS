/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix

/**
 * Handles low-level process and remote HTTP/SSE communication for MCP servers.
 */
class McpTransportDriver(
    private val root: Path,
    private val processRunner: BoundedProcessRunner,
    private val territoryBridge: McpTerritoryBridge,
    private val remoteRequest: ((McpServerConfig, String) -> String)? = null
) {
    fun defaultProbe(
        server: McpServerConfig,
        environment: Map<String, String>,
        headers: Map<String, String>
    ): McpHealth =
        if (server.remote) {
            val initialize = remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", DEFAULT_PROBE_RESPONSE_BYTES, headers)
            remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", DEFAULT_PROBE_RESPONSE_BYTES, headers)
            val toolsList = remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}", DEFAULT_PROBE_RESPONSE_BYTES, headers)
            if (initialize.contains("\"id\":1") && toolsList.contains("\"id\":2")) McpHealth.HEALTHY else McpHealth.UNHEALTHY
        } else {
            probeProcess(server, root, processRunner, environment)
        }

    fun exchangeStdio(
        process: Process,
        toolName: String,
        argumentsJson: String,
        maxResponseBytes: Int,
        toolBudget: McpToolBudget,
        onValidateBudget: (String, String, McpToolBudget) -> Unit
    ): String {
        val writer = process.outputStream.bufferedWriter()
        val reader = process.inputStream.bufferedReader()
        try {
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n")
            writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n")
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n")
            writer.flush()
            val handshake = StringBuilder()
            while (handshake.length < maxResponseBytes) {
                val line = reader.readLine() ?: break
                handshake.append(line).append('\n')
                if (line.contains("\"id\":2")) break
            }
            require(handshake.contains("\"id\":2")) { "MCP tools/list returned no response" }
            onValidateBudget(handshake.toString(), toolName, toolBudget)

            val escapedTool = toolName.replace("\\", "\\\\").replace("\"", "\\\"")
            writer.write("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"$escapedTool\",\"arguments\":$argumentsJson}}\n")
            writer.flush()
            val response = StringBuilder(handshake)
            while (response.length < maxResponseBytes) {
                val line = reader.readLine() ?: break
                response.append(line).append('\n')
                if (line.contains("\"id\":3")) break
            }
            return response.toString().take(maxResponseBytes)
        } finally {
            writer.close()
            reader.close()
        }
    }

    fun remoteCall(
        server: McpServerConfig,
        toolName: String,
        argumentsJson: String,
        maxResponseBytes: Int,
        toolBudget: McpToolBudget,
        headers: Map<String, String>,
        onValidateBudget: (String, String, McpToolBudget) -> Unit
    ): String {
        val initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
        require(remoteExchange(server, initialize, maxResponseBytes, headers).contains("\"id\":1")) { "MCP HTTP initialize returned no response" }
        remoteExchange(server, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", maxResponseBytes, headers)
        val toolsList = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        val toolsListResponse = remoteExchange(server, toolsList, maxResponseBytes, headers)
        require(toolsListResponse.contains("\"id\":2")) { "MCP HTTP tools/list returned no response" }
        onValidateBudget(toolsListResponse, toolName, toolBudget)
        val request = buildString {
            append("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{")
            append("\"name\":\"").append(jsonEscape(toolName)).append("\",")
            append("\"arguments\":").append(argumentsJson).append("}}")
        }
        val response = remoteExchange(server, request, maxResponseBytes, headers)
        require(response.contains("\"id\":3")) { "MCP HTTP tools/call returned no response" }
        return response
    }

    fun remoteExchange(server: McpServerConfig, body: String, maxResponseBytes: Int, headers: Map<String, String>): String {
        val raw = remoteRequest?.invoke(server, body) ?: postRemote(server, body, headers)
        require(raw.toByteArray(StandardCharsets.UTF_8).size <= maxResponseBytes) {
            "MCP HTTP response exceeds the bounded response size"
        }
        return normalizeRemoteResponse(raw)
    }

    private fun normalizeRemoteResponse(raw: String): String {
        val frames = raw.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter(String::isNotBlank)
            .toList()
        return if (frames.isEmpty()) raw else frames.joinToString("\n")
    }

    private fun postRemote(server: McpServerConfig, body: String, headers: Map<String, String>): String {
        val url = server.url?.trim()?.takeIf { it.isNotBlank() }
            ?: error("MCP remote server has no url: ${server.name}")
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "MCP remote url must use http or https: ${server.name}"
        }
        check(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
            "MCP remote request refused: SecretSinkMatrix does not permit network egress"
        }
        val admission = territoryBridge.judge(
            InboundToolRequest(
                source = InboundSource.MCP,
                callerId = "mcp-cli",
                operation = "inspect",
                paths = listOf(".")
            )
        )
        when (admission) {
            is InboundGateResult.Refused -> error("MCP remote request refused by territory bridge: ${admission.reason}")
            is InboundGateResult.Judged -> require(admission.decision.disposition == AgencyDisposition.ALLOWED) {
                "MCP remote request refused by policy: ${admission.decision.reason}"
            }
        }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                headers.forEach { (name, value) ->
                    require(name.matches(HEADER_NAME)) { "MCP header name is invalid: $name" }
                    require(name.lowercase() !in FORBIDDEN_HEADERS) {
                        "MCP header cannot override transport framing: $name"
                    }
                    header(name, value)
                }
            }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())
        require(response.statusCode() in 200..299) {
            "MCP HTTP request failed status=${response.statusCode()} server=${server.name}"
        }
        return response.body().use { input ->
            val bytes = input.readNBytes(DEFAULT_REMOTE_RESPONSE_BYTES + 1)
            require(bytes.size <= DEFAULT_REMOTE_RESPONSE_BYTES) {
                "MCP HTTP response exceeds the bounded response size"
            }
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    companion object {
        const val DEFAULT_PROBE_RESPONSE_BYTES = 64 * 1024
        const val DEFAULT_REMOTE_RESPONSE_BYTES = 256 * 1024
        val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        val FORBIDDEN_HEADERS = setOf("host", "content-length", "transfer-encoding")

        fun probeProcess(
            server: McpServerConfig,
            root: Path,
            processRunner: BoundedProcessRunner,
            environment: Map<String, String>
        ): McpHealth {
            val command = server.command ?: return McpHealth.UNHEALTHY
            val process = processRunner.start(
                listOf(command) + server.args,
                root,
                environment = environment
            )
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n")
                writer.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n")
                writer.write("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n")
                writer.flush()
            }
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
    }
}
