package atropos.core.integration

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class IngestedDocument(
    val originalPath: Path,
    val markdownText: String,
    val casHash: String,
    val sizeBytes: Long
)

class MarkItDownIngestService(private val repoRoot: Path) {
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
        val hash = MessageDigest.getInstance("SHA-256").digest(mdBytes).joinToString("") { "%02x".format(it) }
        val casFile = casDir.resolve(hash).toFile()
        if (!casFile.exists()) {
            casFile.writeBytes(mdBytes)
        }

        return IngestedDocument(file, markdown, hash, mdBytes.size.toLong())
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
}
