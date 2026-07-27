/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

/** One addressable span of a source document. */
data class DloiIndexedSection(
    val id: String,
    val heading: String,
    val startLine: Int,
    val endLine: Int,
    val startParagraph: Int,
    val endParagraph: Int
)

/**
 * Splits a source document into addressable sections.
 *
 * A section runs from a heading up to the line before the next one. Everything
 * before the first heading becomes the preamble section, so no line of an
 * authority document is unaddressable — a line nobody can cite is a line that
 * cannot be used as authority.
 *
 * Ids are positional (`S0001`, `S0002`, …) because a DLOI address is a
 * coordinate: it has to stay stable for a given document, and it is the digest
 * of the bytes that decides whether it is still the same document at all.
 */
object DloiSectionExtractor {

    /**
     * @param lines the document, 0-indexed here and reported 1-indexed, because
     *   addresses are written the way an editor shows them.
     */
    fun extract(lines: List<String>): List<DloiIndexedSection> {
        if (lines.isEmpty()) return emptyList()

        val paragraphOf = paragraphNumbers(lines)
        val headingIndices = lines.indices.filter { isHeading(lines[it]) }

        // Every line before the first heading is still authority, so it gets a
        // section of its own rather than being dropped.
        val starts = if (headingIndices.firstOrNull() == 0) headingIndices else listOf(0) + headingIndices

        return starts.mapIndexed { position, start ->
            val end = starts.getOrNull(position + 1)?.minus(1) ?: lines.lastIndex
            val paragraphs = (start..end).mapNotNull { paragraphOf[it] }

            DloiIndexedSection(
                id = "S%04d".format(position + 1),
                heading = lines[start].trim().ifBlank { "(preamble)" },
                startLine = start + 1,
                endLine = end + 1,
                startParagraph = paragraphs.minOrNull() ?: 0,
                endParagraph = paragraphs.maxOrNull() ?: 0
            )
        }
    }

    /**
     * Paragraph number per line, or `null` for blank lines.
     *
     * A paragraph is a run of non-blank lines; blank lines separate them and
     * belong to none.
     */
    private fun paragraphNumbers(lines: List<String>): List<Int?> {
        var paragraph = 0
        var inParagraph = false
        return lines.map { line ->
            if (line.isBlank()) {
                inParagraph = false
                null
            } else {
                if (!inParagraph) {
                    paragraph++
                    inParagraph = true
                }
                paragraph
            }
        }
    }

    /**
     * Whether a line opens a new section.
     *
     * Recognises the forms the authority documents actually use: markdown ATX
     * headings, and numbered structural headings such as `Phase 7:` or
     * `Stage 2 —`. Deliberately narrow: treating every non-blank line as a
     * heading would make each paragraph its own section and shift every address
     * in every document.
     */
    private fun isHeading(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("#")) return true
        return NUMBERED_HEADING.containsMatchIn(trimmed)
    }

    private val NUMBERED_HEADING =
        Regex("""^(Phase|Stage|Section|Appendix|Part|Priority)\s+[0-9A-Z]+\s*[:.\-–—]""", RegexOption.IGNORE_CASE)
}
