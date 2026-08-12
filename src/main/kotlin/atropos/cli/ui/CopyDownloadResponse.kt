package atropos.cli.ui

import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/** Redacted response copy/download affordance for operator-visible output. */
class CopyDownloadResponse(
    private val filter: RedactionFilter = RedactionFilter(),
    private val maximumBytes: Int = 1_048_576
) {
    data class ResponseArtifact(val text: String, val bytes: Int)

    fun copy(response: String): ResponseArtifact = artifact(response)

    fun download(response: String, target: Path): ResponseArtifact {
        val artifact = artifact(response)
        target.parent?.let(Files::createDirectories)
        Files.writeString(target, artifact.text)
        return artifact
    }

    private fun artifact(response: String): ResponseArtifact {
        val redacted = filter.redact(response)
        val bytes = redacted.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maximumBytes) { "response exceeds copy/download limit" }
        return ResponseArtifact(redacted, bytes.size)
    }
}
