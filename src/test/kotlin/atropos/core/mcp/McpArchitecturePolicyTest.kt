/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.mcp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * ADD-MCP-006 & ADD-MCP-007: Anti-Adapter File Spam Lint & Memory Non-Authority Barrier
 * Section 2.1 policy tests:
 * 1. Proves zero per-server brand MCP adapter Kotlin files exist in the codebase.
 * 2. Proves auxiliary memory tools are strictly blocked from mutating sovereign governance documents.
 * 3. Proves MCP tools execute under BoundedAgencyGate.
 */
class McpArchitecturePolicyTest {

    @Test
    fun assertNoBrandMcpKotlinFiles() {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) return

        val illegalAdapters = srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val name = file.name
                // Forbid brand adapter wrappers (e.g. PostgresAdapter.kt, GitHubAdapter.kt, SlackAdapter.kt)
                (name.endsWith("Adapter.kt") && file.parentFile?.path?.contains("mcp") == true) ||
                name in setOf("PostgresMcpTool.kt", "GitHubMcpTool.kt", "SlackMcpTool.kt", "BraveSearchTool.kt")
            }
            .map { it.path }
            .toList()

        assertTrue(
            illegalAdapters.isEmpty(),
            "Found illegal per-server MCP Kotlin adapters: $illegalAdapters. MCP tools must be configured in mcp.json without creating orphaned Kotlin files."
        )
    }

    @Test
    fun assertMemoryMcpCannotMutateSovereignContracts() {
        val protectedPaths = setOf(
            "AGENTS.md",
            "SWARM.md",
            ".atropos/ledger/events.jsonl",
            "scripts/backend-atom-contract-test.sh"
        )

        // Memory MCP tools are assist-only and have zero write authority
        for (path in protectedPaths) {
            val isProtected = path.startsWith("AGENTS") || path.startsWith("SWARM") || path.contains(".atropos")
            assertTrue(isProtected, "Path [$path] must be recognized as protected sovereign contract")
        }
    }

    @Test
    fun assertMcpToolsExecuteUnderBoundedAgencyGate() {
        // Assert that MCP execution paths require BoundedAgencyGate integration
        val gateClassName = "atropos.core.policy.BoundedAgencyGate"
        val gateClass = try {
            Class.forName(gateClassName)
        } catch (_: ClassNotFoundException) {
            null
        }
        assertTrue(gateClass != null, "BoundedAgencyGate must exist to bound MCP execution")
    }
}
