/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/** What one `ensureIndexed` pass did. */
data class DloiIndexResult(
    val indexed: List<String>,
    val alreadyCurrent: List<String>,
    val pruned: List<String>
) {
    val changed: Boolean get() = indexed.isNotEmpty() || pruned.isNotEmpty()
}

/**
 * Builds the source index that [DloiService] reads.
 *
 * The index was a read-only contract: `loadDocuments()` walked
 * `.atropos/context-cache/source-index/v1/extracted` and returned nothing when
 * it was absent, and nothing in the tree ever wrote it. Exact source authority
 * therefore resolved nothing at all.
 *
 * Entries are **content-addressed**: the identity of a document is the SHA-256
 * of its bytes, and `source_id` is the first 16 hex characters of that digest.
 * Nothing here invents an identity — change a byte and the document becomes a
 * different document, which is what makes an address provable.
 */
class DloiSourceIndexer(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize(),
    /**
     * The authority set. Only documents committed here are addressable — an
     * index built from anything else would be asserting authority the
     * repository never granted.
     */
    private val sourceDirectory: Path = repoRoot.resolve("docs/source")
) {
    private val indexRoot = repoRoot.resolve(".atropos/context-cache/source-index/v1")

    /**
     * Brings the index in line with the authority set.
     *
     * Missing documents are indexed; entries whose digest no longer corresponds
     * to a present document are pruned, so a superseded revision cannot keep
     * answering lookups beside the document that replaced it.
     */
    fun ensureIndexed(): DloiIndexResult {
        if (!Files.isDirectory(sourceDirectory)) {
            return DloiIndexResult(emptyList(), emptyList(), emptyList())
        }

        val indexed = mutableListOf<String>()
        val current = mutableListOf<String>()
        val liveDigests = mutableSetOf<String>()

        sourceDocuments().forEach { file ->
            val bytes = Files.readAllBytes(file)
            val digest = sha256(bytes)
            liveDigests += digest

            val extracted = extractedPath(digest)
            if (Files.isRegularFile(extracted)) {
                current += file.fileName.toString()
                return@forEach
            }
            writeEntry(file, bytes, digest)
            indexed += file.fileName.toString()
        }

        return DloiIndexResult(indexed, current, prune(liveDigests))
    }

    private fun sourceDocuments(): List<Path> =
        Files.list(sourceDirectory).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.lowercase() in TEXT_EXTENSIONS }
                .sorted()
                .toList()
        }

    private fun writeEntry(file: Path, bytes: ByteArray, digest: String) {
        // The byte-order mark is stripped from the content but the line
        // structure is untouched: an address is a line coordinate, so
        // renumbering lines here would silently move every existing address.
        val text = String(bytes, StandardCharsets.UTF_8).removePrefix("﻿").replace("\r\n", "\n")
        val lines = text.split("\n")
        val sections = DloiSectionExtractor.extract(lines)

        val normalized = normalizedPath(digest)
        Files.createDirectories(normalized.parent)
        Files.writeString(normalized, text, StandardCharsets.UTF_8)

        val sourceId = digest.take(SOURCE_ID_LENGTH)
        val extracted = extractedPath(digest)
        Files.createDirectories(extracted.parent)
        Files.writeString(
            extracted,
            renderJson(
                sourceId = sourceId,
                originalFilename = "${sourceId}__${file.fileName}",
                normalizedPath = normalized,
                lineCount = lines.size,
                pageCount = text.count { it == '' } + 1,
                paragraphCount = sections.maxOfOrNull { it.endParagraph } ?: 0,
                sections = sections
            ),
            StandardCharsets.UTF_8
        )
    }

    /** Removes entries whose document is no longer part of the authority set. */
    private fun prune(liveDigests: Set<String>): List<String> {
        val extractedRoot = indexRoot.resolve("extracted")
        if (!Files.isDirectory(extractedRoot)) return emptyList()

        val removed = mutableListOf<String>()
        Files.walk(extractedRoot).use { stream ->
            stream.filter { it.isRegularFile() && it.fileName.toString().endsWith(".v1.json") }.toList()
        }.forEach { entry ->
            val digest = entry.fileName.toString().removeSuffix(".v1.json")
            if (digest !in liveDigests) {
                Files.deleteIfExists(entry)
                Files.deleteIfExists(normalizedPath(digest))
                removed += digest.take(SOURCE_ID_LENGTH)
            }
        }
        return removed
    }

    private fun normalizedPath(digest: String): Path =
        indexRoot.resolve("normalized/${digest.take(2)}/$digest.v1.txt")

    private fun extractedPath(digest: String): Path =
        indexRoot.resolve("extracted/${digest.take(2)}/$digest.v1.json")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun renderJson(
        sourceId: String,
        originalFilename: String,
        normalizedPath: Path,
        lineCount: Int,
        pageCount: Int,
        paragraphCount: Int,
        sections: List<DloiIndexedSection>
    ): String = buildString {
        appendLine("{")
        appendLine("""  "source_id": "$sourceId",""")
        appendLine("""  "original_filename": "${escape(originalFilename)}",""")
        appendLine("""  "kind": "text",""")
        appendLine("""  "normalized_path": "${escape(normalizedPath.toAbsolutePath().normalize().toString())}",""")
        appendLine("""  "line_count": $lineCount,""")
        appendLine("""  "page_count": $pageCount,""")
        appendLine("""  "paragraph_count": $paragraphCount,""")
        appendLine("""  "sections": [""")
        sections.forEachIndexed { index, section ->
            appendLine("    {")
            appendLine("""      "section_id": "${section.id}",""")
            appendLine("""      "heading": "${escape(section.heading)}",""")
            appendLine("""      "start_line": ${section.startLine},""")
            appendLine("""      "end_line": ${section.endLine},""")
            appendLine("""      "start_page": 1,""")
            appendLine("""      "end_page": $pageCount,""")
            appendLine("""      "start_paragraph": ${section.startParagraph},""")
            appendLine("""      "end_paragraph": ${section.endParagraph}""")
            append("    }")
            if (index != sections.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\t", " ")

    private companion object {
        val TEXT_EXTENSIONS = setOf("txt", "md")
        const val SOURCE_ID_LENGTH = 16
    }
}
