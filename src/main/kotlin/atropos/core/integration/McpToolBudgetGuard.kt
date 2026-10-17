/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import org.json.JSONObject

data class FilteredToolBundle(
    val activeTools: List<GenericMcpToolDescriptor>,
    val truncatedCharsCount: Int,
    val droppedToolsCount: Int
)

/**
 * ATOM-MCP-009: MCP 32-Tool Limit & Budget Guard.
 *
 * Enforces the hard ceiling: |RegisteredTools| <= maxBudget (32).
 * Prunes and ranks tool descriptors using userQuery relevance matching.
 * Mathematical Invariant: |RegisteredTools| <= 32 under all conditions.
 */
class McpToolBudgetGuard(
    private val maxBudget: Int = 32,
    private val maxCharacterLimit: Int = 4000
) {
    fun pruneAndEnforce(tools: List<GenericMcpToolDescriptor>, userQuery: String): List<GenericMcpToolDescriptor> {
        if (tools.size <= maxBudget) return tools
        val queryKeywords = userQuery.lowercase()
            .split(Regex("[^a-zA-Z0-9_]"))
            .filter { it.length > 2 }
            .toSet()

        val scoredTools = if (queryKeywords.isEmpty()) {
            tools
        } else {
            tools.map { tool ->
                val text = (tool.name + " " + tool.description).lowercase()
                val matches = queryKeywords.count { text.contains(it) }
                tool to matches
            }.sortedByDescending { it.second }.map { it.first }
        }
        return scoredTools.take(maxBudget)
    }

    fun pruneAndEnforceBundle(tools: List<GenericMcpToolDescriptor>, userQuery: String): FilteredToolBundle {
        val active = pruneAndEnforce(tools, userQuery)
        return FilteredToolBundle(active, 0, tools.size - active.size)
    }
}
