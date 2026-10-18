/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import atropos.cli.help.CommandLeafDocumentation
import atropos.cli.help.CommandLeafRegistry
import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRegistry
import atropos.core.memory.MemoryLayerLattice

/**
 * ATOM-TAX-028: Plugin & MCP Trie Contribution Engine.
 *
 * Dynamically registers active MCP tools into the 4-tier trie taxonomy
 * (under /toolchain integrations mcp <server> <tool>), command registry,
 * and progressive disclosure leaf registry with tamper-evident attestation.
 */
class McpTaxonomyContributor(
    private val taxonomy: HierarchicalCommandTaxonomy = HierarchicalCommandTaxonomy(),
    private val memoryLattice: MemoryLayerLattice = MemoryLayerLattice()
) {
    data class McpToolDeclaration(
        val serverName: String,
        val toolName: String,
        val description: String,
        val inputSchema: String = "{}",
        val requiredCapabilities: List<String> = listOf("CAP_MCP_EXECUTE")
    ) {
        val canonicalCommand: String get() = "/mcp call $serverName $toolName"
    }

    fun contributeTool(tool: McpToolDeclaration): Boolean {
        // 1. Register in 4-tier taxonomy under toolchain -> integrations -> mcp
        val action = "integrations"
        val target = "mcp ${tool.serverName} ${tool.toolName}"
        val leaf = "Invokes allowlisted MCP tool '${tool.toolName}' on server '${tool.serverName}': ${tool.description}"
        taxonomy.register("toolchain", action, target, leaf)

        // 2. Register in Tier 4 progressive disclosure leaf registry
        val leafDoc = CommandLeafDocumentation(
            command = tool.canonicalCommand,
            brief = tool.description,
            syntax = "${tool.canonicalCommand} [arguments-json]",
            examples = listOf("${tool.canonicalCommand} {\"query\":\"test\"}"),
            sideEffects = "Executes external tool via MCP protocol on server '${tool.serverName}'",
            related = listOf("/mcp list", "/mcp test"),
            securityRisk = "L1 (Allowlisted MCP execution)",
            evidenceImpact = "MCP invocation audit receipt recorded",
            costClass = "FREE_LOCAL",
            requiredCapabilities = tool.requiredCapabilities
        )
        CommandLeafRegistry.register(leafDoc)

        // 3. Emit cryptographic evidence receipt
        emitReceipt(tool)
        return true
    }

    fun contributeBatch(tools: List<McpToolDeclaration>): Int {
        var count = 0
        for (tool in tools) {
            if (contributeTool(tool)) count++
        }
        return count
    }

    private fun emitReceipt(tool: McpToolDeclaration) {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:MCP_TOOL_CONTRIBUTED:${tool.serverName}:${tool.toolName}"
        val sha256 = MemoryLayerLattice.sha256(payload)
        val json = "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"MCP_TRIE_CONTRIBUTED\"," +
            "\"actor\":\"McpTaxonomyContributor\",\"surface\":\"CLI\",\"before\":\"none\",\"after\":\"${tool.canonicalCommand}\"}"
        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "mcp_trie_${tool.serverName}_${tool.toolName}_$timestamp",
            content = json
        )
    }
}
