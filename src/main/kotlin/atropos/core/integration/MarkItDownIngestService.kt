/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.dag.DocumentIngestionService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class MarkItDownIngestResult(
    val markdownPath: Path,
    val markdownSha256: String,
    val evidence: McpEvidenceRef,
    val requirements: Int
)

/** Composes the existing MCP host and DAG ingestion owners for MarkItDown. */
class MarkItDownIngestService(
    private val repoRoot: Path,
    private val ingest: DocumentIngestionService = DocumentIngestionService(repoRoot = repoRoot),
    private val call: (String, String, String, List<String>) -> McpToolCallResult
) {
    fun ingest(sourcePath: String): MarkItDownIngestResult {
        val source = repoRoot.resolve(sourcePath).normalize()
        require(source.startsWith(repoRoot.toAbsolutePath().normalize())) { "MarkItDown source must remain under the repository territory" }
        require(Files.isRegularFile(source)) { "MarkItDown source is not a file: $sourcePath" }
        val relative = repoRoot.toAbsolutePath().normalize().relativize(source.toAbsolutePath().normalize()).toString()
        val escaped = relative.replace("\\", "\\\\").replace("\"", "\\\"")
        val result = call("markitdown", "convert_to_markdown", "{\"path\":\"$escaped\"}", listOf(relative))
        val markdown = extractText(result.response).trim()
        require(markdown.isNotBlank()) { "MarkItDown returned no markdown content" }
        val hash = sha256(markdown)
        val targetRoot = repoRoot.resolve(".atropos/mcp/ingest").normalize()
        require(targetRoot.startsWith(repoRoot.toAbsolutePath().normalize())) { "MarkItDown output escaped repository" }
        Files.createDirectories(targetRoot)
        val target = targetRoot.resolve("$hash.md")
        Files.writeString(target, markdown, StandardCharsets.UTF_8)
        val ingested = ingest.ingestText(markdown, "md", "markitdown-$hash")
        check(ingested.success) { "MarkItDown markdown ingestion failed: ${ingested.errors.joinToString("; ")}" }
        return MarkItDownIngestResult(target, hash, result.evidence, ingested.requirements.size)
    }

    private fun extractText(response: String): String {
        val match = Regex("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(response)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: response
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
