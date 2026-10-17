/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.dag.DocumentIngestionService
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class IngestedDocument(
    val originalPath: Path,
    val markdownText: String,
    val casHash: String,
    val sizeBytes: Long
)

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
    private val call: ((String, String, String, List<String>) -> McpToolCallResult)? = null
) {
    constructor(
        repoRoot: Path,
        call: (String, String, String, List<String>) -> McpToolCallResult
    ) : this(repoRoot, DocumentIngestionService(repoRoot = repoRoot), call)

    private val supportedExts = setOf("pdf", "docx", "pptx", "xlsx", "html", "md", "txt")
    private val casDir = repoRoot.resolve(".atropos/cas")

    init {
        Files.createDirectories(casDir)
    }

    fun isSupported(file: Path): Boolean {
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        return ext in supportedExts
    }

    fun ingestToMarkdown(file: Path): IngestedDocument {
        require(Files.exists(file)) { "Source document does not exist: $file" }
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()

        val markdown = if (ext == "md" || ext == "txt") {
            Files.readString(file, StandardCharsets.UTF_8)
        } else {
            convertViaSubprocess(file)
        }

        val mdBytes = markdown.toByteArray(StandardCharsets.UTF_8)
        val hash = sha256(markdown)
        val casFile = casDir.resolve(hash).toFile()
        if (!casFile.exists()) {
            casFile.writeBytes(mdBytes)
        }

        atropos.core.thinking.Thinking.step("mcp", "MarkItDown ingested ${file.fileName} into CAS")
        atropos.core.thinking.Thinking.detail("mcp", "Document converted to markdown: hash=${hash.take(12)}... (${mdBytes.size} bytes)")
        return IngestedDocument(file, markdown, hash, mdBytes.size.toLong())
    }

    fun ingest(sourcePath: String): MarkItDownIngestResult {
        val caller = call ?: throw IllegalStateException("Tool call invoker required for MCP-backed MarkItDown ingestion")
        val source = repoRoot.resolve(sourcePath).normalize()
        require(source.startsWith(repoRoot.toAbsolutePath().normalize())) { "MarkItDown source must remain under the repository territory" }
        require(!Files.isSymbolicLink(source)) { "MarkItDown source must not be a symbolic link" }
        require(Files.isRegularFile(source)) { "MarkItDown source is not a file: $sourcePath" }
        require(Files.size(source) <= MAX_SOURCE_BYTES) {
            "MarkItDown source exceeds the ${MAX_SOURCE_BYTES / (1024 * 1024)} MiB limit"
        }
        val relative = repoRoot.toAbsolutePath().normalize().relativize(source.toAbsolutePath().normalize()).toString()
        val escaped = relative.replace("\\", "\\\\").replace("\"", "\\\"")
        val result = caller("markitdown", "convert_to_markdown", "{\"path\":\"$escaped\"}", listOf(relative))
        val markdown = extractText(result.response).trim()
        require(markdown.isNotBlank()) { "MarkItDown returned no markdown content" }
        require(markdown.toByteArray(StandardCharsets.UTF_8).size <= MAX_MARKDOWN_BYTES) {
            "MarkItDown markdown exceeds the ${MAX_MARKDOWN_BYTES / (1024 * 1024)} MiB limit"
        }
        val hash = sha256(markdown)
        val targetRoot = repoRoot.resolve(".atropos/mcp/ingest").normalize()
        require(targetRoot.startsWith(repoRoot.toAbsolutePath().normalize())) { "MarkItDown output escaped repository" }
        Files.createDirectories(targetRoot)
        val target = targetRoot.resolve("$hash.md")
        val temporary = Files.createTempFile(targetRoot, ".markitdown-", ".tmp")
        try {
            Files.writeString(temporary, markdown, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        val ingested = ingest.ingestText(markdown, "md", "markitdown-$hash")
        check(ingested.success) { "MarkItDown markdown ingestion failed: ${ingested.errors.joinToString("; ")}" }
        atropos.core.thinking.Thinking.step("mcp", "MarkItDown processed $sourcePath (${ingested.requirements.size} requirements extracted)")
        atropos.core.thinking.Thinking.detail("mcp", "Generated target: $target (hash=$hash)")
        return MarkItDownIngestResult(target, hash, result.evidence, ingested.requirements.size)
    }

    private fun convertViaSubprocess(file: Path): String {
        return try {
            val pb = ProcessBuilder("markitdown", file.toAbsolutePath().toString())
            val proc = pb.start()
            val text = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            proc.waitFor(30, TimeUnit.SECONDS)
            if (proc.exitValue() == 0 && text.isNotBlank()) text else fallbackRead(file)
        } catch (_: Exception) {
            fallbackRead(file)
        }
    }

    private fun fallbackRead(file: Path): String =
        "# Document: ${file.fileName}\n[Binary conversion requires `pip install markitdown`]"

    private fun extractText(response: String): String {
        val match = Regex("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(response)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: response
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_SOURCE_BYTES = 8 * 1024 * 1024
        const val MAX_MARKDOWN_BYTES = 8 * 1024 * 1024
    }
}
