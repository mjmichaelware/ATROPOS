package atropos.core.integration

import org.json.JSONObject

data class FilteredToolBundle(
    val activeTools: List<GenericMcpToolDescriptor>,
    val truncatedCharsCount: Int,
    val droppedToolsCount: Int
)

class McpToolBudgetGuard(
    private val maxToolsTotal: Int = 32,
    private val maxCharacterLimit: Int = 4000
) {
    fun pruneAndEnforce(tools: List<GenericMcpToolDescriptor>, userQuery: String): FilteredToolBundle {
        if (tools.isEmpty()) return FilteredToolBundle(emptyList(), 0, 0)
        val queryKeywords = userQuery.lowercase().split(Regex("[^a-zA-Z0-9_]")).filter { it.length > 2 }.toSet()

        val scoredTools = tools.map { tool ->
            val text = (tool.name + " " + tool.description).lowercase()
            val matches = queryKeywords.count { text.contains(it) }
            tool to matches
        }.sortedByDescending { it.second }.map { it.first }

        val cappedTools = scoredTools.take(maxToolsTotal)
        val dropped = tools.size - cappedTools.size

        val finalizedTools = mutableListOf<GenericMcpToolDescriptor>()
        var totalChars = 0
        var truncatedChars = 0

        for (tool in cappedTools) {
            val compactDesc = if (tool.description.length > 180) {
                truncatedChars += (tool.description.length - 180)
                tool.description.take(177) + "..."
            } else {
                tool.description
            }

            val schemaObj = try { JSONObject(tool.inputSchemaJson) } catch (_: Exception) { JSONObject() }
            schemaObj.remove("\$schema")
            schemaObj.remove("title")
            val compactSchema = schemaObj.toString()

            val toolChars = tool.name.length + compactDesc.length + compactSchema.length
            if (totalChars + toolChars <= maxCharacterLimit) {
                finalizedTools.add(tool.copy(description = compactDesc, inputSchemaJson = compactSchema))
                totalChars += toolChars
            } else {
                break
            }
        }

        atropos.core.thinking.Thinking.step("mcp", "Tool budget guard active: ${finalizedTools.size} tools admitted")
        atropos.core.thinking.Thinking.detail("mcp", "Budget evaluation: ${tools.size} input -> ${finalizedTools.size} active, $truncatedChars chars trimmed, $dropped dropped")

        return FilteredToolBundle(
            activeTools = finalizedTools,
            truncatedCharsCount = truncatedChars,
            droppedToolsCount = tools.size - finalizedTools.size
        )
    }
}
