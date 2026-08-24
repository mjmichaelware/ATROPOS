/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkItDownIngestServiceTest {
    @Test
    fun markitdown_result_becomes_hashed_markdown_and_dag_document() {
        val root = Files.createTempDirectory("markitdown-ingest")
        Files.writeString(root.resolve("source.txt"), "source")
        val service = MarkItDownIngestService(root) { _, _, _, _ ->
            McpToolCallResult(
                "{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"# Converted\\n\\nMust verify\\n\"}]}}",
                McpEvidenceRef("evidence-hash", null, null)
            )
        }
        val result = service.ingest("source.txt")
        assertTrue(Files.isRegularFile(result.markdownPath))
        assertTrue(result.markdownSha256.length == 64)
        assertTrue(result.requirements > 0)
        assertTrue(Files.exists(root.resolve(".atropos/dag")))
    }

    @Test
    fun oversized_source_is_rejected_before_mcp_call() {
        val root = Files.createTempDirectory("markitdown-size")
        Files.write(root.resolve("source.bin"), ByteArray(8 * 1024 * 1024 + 1))
        var called = false
        val service = MarkItDownIngestService(root) { _, _, _, _ ->
            called = true
            error("MCP must not be called for an oversized source")
        }

        assertFailsWith<IllegalArgumentException> { service.ingest("source.bin") }
        assertTrue(!called)
    }
}
