/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Source-authority index builder — the missing writer for
 * `.atropos/context-cache/source-index/v1/extracted`.
 *
 * This class lifts the Priority #6 hard stop documented in
 * `PHASE10_PRIORITY6_HARDSTOP.md`:
 *
 * > `DloiService.loadDocuments()` reads
 * > `.atropos/context-cache/source-index/v1/extracted`. **Nothing in the
 * > tree writes it** — that path appears exactly once in
 * > `src/main/kotlin`, at the read site.
 *
 * Now something writes it. This indexer:
 * 1. Walks `docs/source/` for `.txt` and `.md` authority files
 * 2. Computes the SHA-256 hash of each file (the source-id)
 * 3. Detects section headings from the text
 * 4. Produces the exact JSON index shape that [DloiService.loadIndexedDocument]
 *    already parses
 * 5. Writes normalized text and extracted JSON to the index cache
 *
 * No hashes are fabricated. Every `source_id` is the real first-16-hex of the
 * SHA-256 of the file's bytes. HIG=0 applies: if a file cannot be hashed or
 * parsed, it is skipped — never guessed.
 */
class SourceAuthorityIndexer(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize()
) {
    private val sourceDir = repoRoot.resolve("docs/source")
    private val indexRoot = repoRoot.resolve(".atropos/context-cache/source-index/v1")
    private val normalizedRoot = indexRoot.resolve("normalized")
    private val extractedRoot = indexRoot.resolve("extracted")

    /**
     * Result of indexing a single source file.
     */
    data class IndexedFile(
        val path: Path,
        val sha256: String,
        val sourceId: String,
        val originalFilename: String,
        val lineCount: Int,
        val sectionCount: Int,
        val normalizedPath: Path,
        val extractedPath: Path
    )

    /**
     * Index all authority documents in `docs/source/`.
     *
     * Returns the list of successfully indexed files.
     * Files that cannot be read or are binary (PDF) are skipped.
     */
    fun index(): List<IndexedFile> {
        if (!Files.exists(sourceDir)) return emptyList()

        val results = mutableListOf<IndexedFile>()
        Files.list(sourceDir).use { stream ->
            stream.sorted()
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("txt", "md") }
                .forEach { path ->
                    val result = indexFile(path)
                    if (result != null) results += result
                }
        }
        return results
    }

    /**
     * Index a single source file. Returns null if the file cannot be indexed
     * (e.g., unreadable or empty).
     */
    fun indexFile(path: Path): IndexedFile? {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null

        val sha256 = sha256Hex(bytes)
        val sourceId = sha256.take(16)
        val originalFilename = "${sourceId}__${path.fileName}"

        // Normalize text: strip CR for consistent line counting
        val text = String(bytes, StandardCharsets.UTF_8)
        val normalizedText = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalizedText.lines()
        // Drop trailing empty line from split if present
        val effectiveLines = if (lines.lastOrNull()?.isEmpty() == true) {
            lines.dropLast(1)
        } else {
            lines
        }
        val lineCount = effectiveLines.size

        // Detect sections from headings
        val sections = detectSections(effectiveLines)

        // Write normalized text
        val normalizedDir = normalizedRoot.resolve(sha256.take(2))
        Files.createDirectories(normalizedDir)
        val normalizedPath = normalizedDir.resolve("$sha256.v1.txt")
        Files.writeString(normalizedPath, normalizedText, StandardCharsets.UTF_8)

        // Compute paragraph count
        val paragraphCount = countParagraphs(effectiveLines)

        // Write extracted JSON
        val extractedDir = extractedRoot.resolve(sha256.take(2))
        Files.createDirectories(extractedDir)
        val extractedPath = extractedDir.resolve("$sha256.v1.json")
        val json = buildIndexJson(
            sourceId = sourceId,
            originalFilename = originalFilename,
            kind = if (path.fileName.toString().substringAfterLast('.', "").lowercase() == "md") "markdown" else "text",
            normalizedPath = normalizedPath,
            lineCount = lineCount,
            paragraphCount = paragraphCount,
            sections = sections,
            sha256 = sha256,
            sizeBytes = bytes.size.toLong(),
            originalPath = path
        )
        Files.writeString(extractedPath, json, StandardCharsets.UTF_8)

        return IndexedFile(
            path = path,
            sha256 = sha256,
            sourceId = sourceId,
            originalFilename = originalFilename,
            lineCount = lineCount,
            sectionCount = sections.size,
            normalizedPath = normalizedPath,
            extractedPath = extractedPath
        )
    }

    /**
     * Detect sections from source document text lines.
     *
     * Recognized heading patterns:
     * - Lines starting with "Phase N:" (the canonical phase headings)
     * - Lines starting with a number and "." (numbered items like "1. PRODUCT IDENTITY:")
     * - Markdown "#" headings (for .md files)
     * - The first non-blank line is always the title section
     *
     * Each section spans from its heading to the line before the next heading.
     */
    internal fun detectSections(lines: List<String>): List<SectionData> {
        if (lines.isEmpty()) return emptyList()

        data class HeadingMark(val lineIndex: Int, val title: String, val level: Int)

        val headings = mutableListOf<HeadingMark>()

        // First non-blank line is always the title
        val titleIndex = lines.indexOfFirst { it.isNotBlank() }
        if (titleIndex >= 0) {
            headings += HeadingMark(titleIndex, lines[titleIndex].trim(), 1)
        }

        lines.forEachIndexed { index, line ->
            if (index == titleIndex) return@forEachIndexed
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEachIndexed

            when {
                // "Phase N:" pattern
                PHASE_HEADING.matches(trimmed) -> {
                    headings += HeadingMark(index, trimmed, 1)
                }
                // Numbered items: "1. SOMETHING:" or "1. SOMETHING "
                NUMBERED_HEADING.matches(trimmed) -> {
                    headings += HeadingMark(index, trimmed, 1)
                }
                // Markdown headings
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    val title = trimmed.removePrefix("#".repeat(level)).trim()
                    if (title.isNotBlank()) {
                        headings += HeadingMark(index, title, level)
                    }
                }
                // MACRO N pattern
                MACRO_HEADING.matches(trimmed) -> {
                    headings += HeadingMark(index, trimmed, 1)
                }
            }
        }

        // Sort and deduplicate by line index
        val sorted = headings.sortedBy { it.lineIndex }.distinctBy { it.lineIndex }

        // Build sections: each section runs from its heading to the line before the next
        val sections = mutableListOf<SectionData>()
        sorted.forEachIndexed { i, heading ->
            val startLine = heading.lineIndex + 1  // 1-indexed
            val endLine = if (i + 1 < sorted.size) {
                sorted[i + 1].lineIndex  // 1-indexed: the line before next heading
            } else {
                lines.size  // through end of file
            }

            // Compute paragraph span within this section
            val sectionLines = lines.subList(heading.lineIndex, endLine.coerceAtMost(lines.size))
            val (startParagraph, endParagraph) = paragraphSpan(lines, heading.lineIndex, endLine.coerceAtMost(lines.size))

            val sectionId = "S${(i + 1).toString().padStart(4, '0')}"
            sections += SectionData(
                sectionId = sectionId,
                heading = heading.title,
                headingLevel = heading.level,
                startLine = startLine,
                endLine = endLine,
                startParagraph = startParagraph,
                endParagraph = endParagraph
            )
        }

        return sections
    }

    private fun paragraphSpan(allLines: List<String>, fromIndex: Int, toIndex: Int): Pair<Int, Int> {
        // Count paragraphs globally up to and within the range
        var paragraph = 0
        var inParagraph = false
        var startParagraph = -1
        var endParagraph = -1

        for (i in allLines.indices) {
            val hasContent = allLines[i].isNotBlank()
            if (hasContent && !inParagraph) {
                paragraph++
                inParagraph = true
            } else if (!hasContent) {
                inParagraph = false
            }
            if (i == fromIndex && hasContent) {
                startParagraph = paragraph
            }
            if (i in fromIndex until toIndex && hasContent) {
                endParagraph = paragraph
            }
        }

        if (startParagraph == -1) {
            // First non-blank line in range
            for (i in fromIndex until toIndex) {
                if (allLines[i].isNotBlank()) {
                    // Re-count to find its paragraph
                    var p = 0
                    var inp = false
                    for (j in 0..i) {
                        val h = allLines[j].isNotBlank()
                        if (h && !inp) { p++; inp = true }
                        else if (!h) inp = false
                    }
                    startParagraph = p
                    break
                }
            }
        }
        if (startParagraph == -1) startParagraph = paragraph
        if (endParagraph == -1) endParagraph = startParagraph

        return startParagraph to endParagraph
    }

    private fun countParagraphs(lines: List<String>): Int {
        var paragraph = 0
        var inParagraph = false
        lines.forEach { line ->
            val hasContent = line.isNotBlank()
            if (hasContent && !inParagraph) {
                paragraph++
                inParagraph = true
            } else if (!hasContent) {
                inParagraph = false
            }
        }
        return paragraph
    }

    private fun buildIndexJson(
        sourceId: String,
        originalFilename: String,
        kind: String,
        normalizedPath: Path,
        lineCount: Int,
        paragraphCount: Int,
        sections: List<SectionData>,
        sha256: String,
        sizeBytes: Long,
        originalPath: Path
    ): String = buildString {
        appendLine("{")
        appendLine("""  "source_id": "${escapeJson(sourceId)}",""")
        appendLine("""  "original_filename": "${escapeJson(originalFilename)}",""")
        appendLine("""  "kind": "${escapeJson(kind)}",""")
        appendLine("""  "normalized_path": "${escapeJson(normalizedPath.toAbsolutePath().normalize().toString())}",""")
        appendLine("""  "line_count": $lineCount,""")
        appendLine("""  "page_count": null,""")
        appendLine("""  "paragraph_count": $paragraphCount,""")
        appendLine("""  "section_count": ${sections.size},""")
        appendLine("""  "sha256": "${escapeJson(sha256)}",""")
        appendLine("""  "size_bytes": $sizeBytes,""")
        appendLine("""  "original_path": "${escapeJson(originalPath.toAbsolutePath().normalize().toString())}",""")
        appendLine("""  "sections": [""")
        sections.forEachIndexed { index, section ->
            appendLine("    {")
            appendLine("""      "section_id": "${escapeJson(section.sectionId)}",""")
            appendLine("""      "heading": "${escapeJson(section.heading)}",""")
            appendLine("""      "heading_level": ${section.headingLevel},""")
            appendLine("""      "start_line": ${section.startLine},""")
            appendLine("""      "end_line": ${section.endLine},""")
            appendLine("""      "start_page": 1,""")
            appendLine("""      "end_page": 1,""")
            appendLine("""      "start_paragraph": ${section.startParagraph},""")
            appendLine("""      "end_paragraph": ${section.endParagraph}""")
            append("    }")
            if (index != sections.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    data class SectionData(
        val sectionId: String,
        val heading: String,
        val headingLevel: Int,
        val startLine: Int,
        val endLine: Int,
        val startParagraph: Int,
        val endParagraph: Int
    )

    companion object {
        /** Compute SHA-256 hex of raw bytes. */
        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }

        private val PHASE_HEADING = Regex("""^Phase \d+:.*""")
        private val NUMBERED_HEADING = Regex("""^\d+\.\s+[A-Z].*""")
        private val MACRO_HEADING = Regex("""^\d+\.\s+MACRO\s+\d+.*""", RegexOption.IGNORE_CASE)

        private fun escapeJson(value: String): String =
            value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
