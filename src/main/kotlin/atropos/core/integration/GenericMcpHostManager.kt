package atropos.core.integration

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

data class GenericMcpServerConfig(
    val id: String,
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null
)

data class GenericMcpToolDescriptor(
    val serverId: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

class GenericMcpHostManager(private val configRoot: Path) {
    private val sessions = ConcurrentHashMap<String, ActiveProcessSession>()
    private val requestId = AtomicLong(1)

    private class ActiveProcessSession(
        val process: Process,
        val writer: BufferedWriter,
        val reader: BufferedReader
    )

    fun loadServerConfigs(): List<GenericMcpServerConfig> {
        val configFile = configRoot.resolve("mcp.json").toFile()
        if (!configFile.exists()) return emptyList()
        val root = JSONObject(configFile.readText(StandardCharsets.UTF_8))
        val servers = root.optJSONObject("mcpServers") ?: return emptyList()
        val configs = servers.keySet().map { key ->
            val obj = servers.getJSONObject(key)
            val cmd = obj.getString("command")
            val args = obj.optJSONArray("args")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            val env = obj.optJSONObject("env")?.let { envObj ->
                envObj.keySet().associateWith { envObj.getString(it) }
            } ?: emptyMap()
            GenericMcpServerConfig(key, cmd, args, env)
        }
        atropos.core.thinking.Thinking.detail("mcp", "Loaded ${configs.size} MCP server configurations from ${configFile.name}")
        return configs
    }

    @Synchronized
    fun startServer(config: GenericMcpServerConfig): Boolean {
        if (sessions.containsKey(config.id)) return true
        atropos.core.thinking.Thinking.outline("mcp", "Connecting MCP integration: ${config.id}")
        atropos.core.thinking.Thinking.step("mcp", "Starting MCP server ${config.id}")
        atropos.core.thinking.Thinking.detail("mcp", "Spawning MCP process for ${config.id}: ${config.command} ${config.args.joinToString(" ")}")
        val pb = ProcessBuilder(listOf(config.command) + config.args)
        config.workingDir?.let { pb.directory(File(it)) }
        pb.environment().putAll(config.env)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)

        val proc = pb.start()
        val writer = BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8))
        val session = ActiveProcessSession(proc, writer, reader)
        sessions[config.id] = session

        val initReq = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", requestId.getAndIncrement())
            put("method", "initialize")
            put("params", JSONObject().apply {
                put("protocolVersion", "2026-07-28")
                put("clientInfo", JSONObject().apply {
                    put("name", "atropos-engine")
                    put("version", "1.0.0")
                })
                put("capabilities", JSONObject().apply {
                    put("tools", JSONObject())
                })
            })
        }
        writer.write(initReq.toString() + "\n")
        writer.flush()

        val respLine = reader.readLine() ?: return false
        val initResp = JSONObject(respLine)
        if (!initResp.has("result")) return false

        val initializedNotif = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }
        writer.write(initializedNotif.toString() + "\n")
        writer.flush()
        return true
    }

    fun listTools(serverId: String): List<GenericMcpToolDescriptor> {
        val session = sessions[serverId] ?: return emptyList()
        val id = requestId.getAndIncrement()
        val req = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/list")
            put("params", JSONObject())
        }
        val resp = sendAndAwait(session, req, 10) ?: return emptyList()
        val result = resp.optJSONObject("result") ?: return emptyList()
        val toolsArray = result.optJSONArray("tools") ?: return emptyList()

        val descriptors = (0 until toolsArray.length()).map { i ->
            val t = toolsArray.getJSONObject(i)
            GenericMcpToolDescriptor(
                serverId = serverId,
                name = t.getString("name"),
                description = t.optString("description", ""),
                inputSchemaJson = t.optJSONObject("inputSchema")?.toString() ?: "{}"
            )
        }
        atropos.core.thinking.Thinking.step("mcp", "Registered ${descriptors.size} tools from MCP server $serverId")
        atropos.core.thinking.Thinking.detail("mcp", "Server $serverId tool catalog: [${descriptors.joinToString { it.name }}]")
        return descriptors
    }

    fun callTool(serverId: String, toolName: String, argumentsJson: String, timeoutSeconds: Long = 15): String {
        val session = sessions[serverId] ?: return "ERROR: Server $serverId not running"
        atropos.core.thinking.Thinking.step("mcp", "Invoking MCP tool $toolName on server $serverId")
        atropos.core.thinking.Thinking.detail("mcp", "Dispatched JSON-RPC 2.0 call to $serverId:$toolName (${argumentsJson.length} bytes payload)")
        val id = requestId.getAndIncrement()
        val req = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", toolName)
                put("arguments", JSONObject(argumentsJson))
            })
        }
        val resp = sendAndAwait(session, req, timeoutSeconds) ?: return "ERROR: Tool invocation timed out"
        if (resp.has("error")) {
            return "ERROR: " + resp.getJSONObject("error").optString("message", "Unknown error")
        }
        val result = resp.optJSONObject("result") ?: return "{}"
        val content = result.optJSONArray("content") ?: return "{}"
        val output = (0 until content.length()).map { i ->
            val item = content.getJSONObject(i)
            item.optString("text", item.toString())
        }.joinToString("\n")
        atropos.core.thinking.Thinking.detail("mcp", "Received response from $serverId:$toolName (${output.length} characters)")
        return output
    }

    private fun sendAndAwait(session: ActiveProcessSession, req: JSONObject, timeoutSeconds: Long): JSONObject? {
        synchronized(session) {
            session.writer.write(req.toString() + "\n")
            session.writer.flush()

            val future = java.util.concurrent.CompletableFuture.supplyAsync {
                session.reader.readLine()
            }
            return try {
                val line = future.get(timeoutSeconds, TimeUnit.SECONDS) ?: return null
                JSONObject(line)
            } catch (t: Throwable) {
                null
            }
        }
    }

    fun shutdownServer(serverId: String) {
        sessions.remove(serverId)?.let { session ->
            try {
                atropos.core.thinking.Thinking.detail("mcp", "Terminating MCP server $serverId process")
                session.writer.close()
                session.reader.close()
                session.process.destroyForcibly()
            } catch (_: Exception) {}
        }
    }

    fun shutdownAll() {
        sessions.keys.forEach { shutdownServer(it) }
    }
}
