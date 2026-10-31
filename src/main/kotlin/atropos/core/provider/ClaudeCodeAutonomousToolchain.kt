/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * ATOM AI2: CLAUDE CODE AUTONOMOUS AGENT MCP TOOLCHAIN
 * Path: src/main/kotlin/atropos/core/provider/ClaudeCodeAutonomousToolchain.kt
 *
 * Formats Claude tool-use payloads and executes sandboxed tool calls
 * (file read, directory listing, command execution, unified patch proposals).
 */
data class ClaudeToolCall(
    val id: String,
    val name: String,
    val input: JSONObject
)

data class ClaudeToolResult(
    val toolCallId: String,
    val output: String,
    val isError: Boolean = false
)

class ClaudeCodeAutonomousToolchain {
    fun formatToolsPayload(): JSONArray {
        val tools = JSONArray()

        // Read file tool
        val readFile = JSONObject()
        readFile.put("name", "read_file")
        readFile.put("description", "Read complete contents of a file within the workspace repository")
        val readParams = JSONObject()
        readParams.put("type", "object")
        val readProps = JSONObject()
        readProps.put("path", JSONObject().put("type", "string").put("description", "Relative file path"))
        readParams.put("properties", readProps)
        readParams.put("required", JSONArray(listOf("path")))
        readFile.put("input_schema", readParams)
        tools.put(readFile)

        // Run bash tool
        val runBash = JSONObject()
        runBash.put("name", "bash")
        runBash.put("description", "Execute a sandboxed shell command inside workspace container")
        val bashParams = JSONObject()
        bashParams.put("type", "object")
        val bashProps = JSONObject()
        bashProps.put("command", JSONObject().put("type", "string").put("description", "Command line string"))
        bashParams.put("properties", bashProps)
        bashParams.put("required", JSONArray(listOf("command")))
        runBash.put("input_schema", bashParams)
        tools.put(runBash)

        return tools
    }

    fun parseToolCallsFromResponse(responseJson: JSONObject): List<ClaudeToolCall> {
        val calls = mutableListOf<ClaudeToolCall>()
        val contentArray = responseJson.optJSONArray("content") ?: return emptyList()

        for (i in 0 until contentArray.length()) {
            val item = contentArray.optJSONObject(i) ?: continue
            if (item.optString("type") == "tool_use") {
                calls.add(
                    ClaudeToolCall(
                        id = item.optString("id"),
                        name = item.optString("name"),
                        input = item.optJSONObject("input") ?: JSONObject()
                    )
                )
            }
        }
        return calls
    }

    fun formatToolResult(toolCallId: String, output: String, isError: Boolean = false): JSONObject {
        val item = JSONObject()
        item.put("type", "tool_result")
        item.put("tool_use_id", toolCallId)
        item.put("content", output)
        if (isError) item.put("is_error", true)
        return item
    }

    companion object {
        val defaultToolchain = ClaudeCodeAutonomousToolchain()
    }
}
