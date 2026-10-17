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

data class McpServerConfig(
    val id: String,
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val workingDir: String? = null
)

data class McpToolDescriptor(
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

    fun loadServerConfigs(): List<McpServerConfig> {
        val configFile = configRoot.resolve("mcp.json").toFile()
        if (!configFile.exists()) return emptyList()
        val root = JSONObject(configFile.readText(StandardCharsets.UTF_8))
        val servers = root.optJSONObject("mcpServers") ?: return emptyList()
        return servers.keySet().map { key ->
            val obj = servers.getJSONObject(key)
            val cmd = obj.getString("command")
            val args = obj.optJSONArray("args")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            val env = obj.optJSONObject("env")?.let { envObj ->
                envObj.keySet().associateWith { envObj.getString(it) }
            } ?: emptyMap()
            McpServerConfig(key, cmd, args, env)
        }
    }

    @Synchronized
    fun startServer(config: McpServerConfig): Boolean {
        if (sessions.containsKey(config.id)) return true
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

    fun listTools(serverId: String): List<McpToolDescriptor> {
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

        return (0 until toolsArray.length()).map { i ->
            val t = toolsArray.getJSONObject(i)
            McpToolDescriptor(
                serverId = serverId,
                name = t.getString("name"),
                description = t.optString("description", ""),
                inputSchemaJson = t.optJSONObject("inputSchema")?.toString() ?: "{}"
            )
        }
    }

    fun callTool(serverId: String, toolName: String, argumentsJson: String, timeoutSeconds: Long = 15): String {
        val session = sessions[serverId] ?: return "ERROR: Server $serverId not running"
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
        return (0 until content.length()).map { i ->
            val item = content.getJSONObject(i)
            item.optString("text", item.toString())
        }.joinToString("\n")
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
