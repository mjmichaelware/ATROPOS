/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import atropos.core.artifact.ArtifactHasher
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * A file the operator attached, with the part of it a prompt can carry.
 *
 * `SUP.ART.AT-MENTION-UPLOAD` requires ingestion to be "territory-bounded,
 * size-bounded, and attested". [MentionResolver] owns the first two. This owns
 * the third, and the thing that was missing entirely: actually reading the
 * bytes.
 *
 * @param text the file's content when it is text, `null` when it is not.
 *   Binary is described rather than transcribed — a PNG's bytes in a prompt are
 *   noise that costs the model its context window and tells it nothing.
 * @param truncated whether [text] is shorter than the file. Recorded because a
 *   silently clipped document is worse than a refused one: the operator asks a
 *   question about page nine and gets a confident answer drawn from page one.
 */
data class IngestedAttachment(
    val path: Path,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val sha256: String,
    val text: String?,
    val truncated: Boolean
) {
    val isText: Boolean get() = text != null

    /**
     * The block that goes into a prompt.
     *
     * Fenced and labelled with the file's own name so the model can tell the
     * operator's question from the document they attached, and so a document
     * that itself contains a question cannot be read as the request.
     */
    fun promptBlock(): String = buildString {
        append("--- attached file: ").append(name)
        append(" (").append(sizeBytes).append(" bytes, sha256=").append(sha256.take(16)).append(')')
        if (truncated) append(" [truncated]")
        appendLine()
        if (text != null) {
            appendLine(text)
        } else {
            appendLine("(binary $extension file; contents not included)")
        }
        append("--- end of ").append(name)
    }

    /** The line an evidence bundle records for this attachment. */
    fun evidence(): String =
        "attachment name=$name bytes=$sizeBytes sha256=$sha256 text=$isText truncated=$truncated"
}

/**
 * Reads a resolved mention into an [IngestedAttachment].
 *
 * Separate from [MentionResolver] for the same reason the scanner is separate
 * from the resolver: deciding whether a file *may* be read and actually reading
 * it are different jobs with different failure modes. The resolver is the
 * security boundary and touches nothing; this runs only on paths that boundary
 * already approved.
 */
class AttachmentReader(
    /**
     * The most text one attachment may contribute to a prompt.
     *
     * Far below the 8 MiB ingest ceiling on purpose. The ceiling asks whether a
     * file may enter the system at all; this asks how much of it fits in one
     * request without crowding out the operator's own words. A file over it is
     * carried truncated and says so, rather than being refused — a long
     * document is the normal case for this feature, not an error.
     */
    private val maxPromptChars: Int = DEFAULT_MAX_PROMPT_CHARS,
    private val readBytes: (Path) -> ByteArray = Files::readAllBytes
) {

    fun read(resolution: MentionResolution.Resolved): IngestedAttachment? {
        val bytes = runCatching { readBytes(resolution.path) }.getOrNull() ?: return null
        val name = resolution.path.fileName?.toString().orEmpty()
        val sha = ArtifactHasher.sha256Bytes(bytes)

        if (resolution.extension !in TEXT_EXTENSIONS) {
            return IngestedAttachment(
                path = resolution.path,
                name = name,
                extension = resolution.extension,
                sizeBytes = bytes.size.toLong(),
                sha256 = sha,
                text = null,
                truncated = false
            )
        }

        val decoded = String(bytes, StandardCharsets.UTF_8)
        val truncated = decoded.length > maxPromptChars
        return IngestedAttachment(
            path = resolution.path,
            name = name,
            extension = resolution.extension,
            sizeBytes = bytes.size.toLong(),
            sha256 = sha,
            text = if (truncated) decoded.take(maxPromptChars) else decoded,
            truncated = truncated
        )
    }

    private companion object {
        const val DEFAULT_MAX_PROMPT_CHARS = 200_000

        /**
         * Extensions whose bytes are text.
         *
         * `docx` and `pdf` are ingestible and are deliberately absent: both are
         * containers, and decoding them is a different job with its own
         * failure modes. Until something owns that, they arrive described
         * rather than transcribed, which is true, where handing the model a
         * zip archive's bytes as if they were prose would not be.
         */
        val TEXT_EXTENSIONS = setOf("txt", "md")
    }
}
