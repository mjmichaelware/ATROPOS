/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * A `.docx` read as markdown, using nothing but the JDK.
 *
 * `@spec.docx` resolved, passed territory, passed the size ceiling, and then
 * arrived as `(binary docx file; contents not included)` — the operator was
 * told "attached" and the model was given a sentence saying there was a file.
 * A docx is a zip holding XML, and the JDK has both, so the only reason it
 * stayed unread was that nothing had been written to read it.
 *
 * ## Markdown, not flat text
 *
 * Headings become `#` runs and numbered/bulleted paragraphs become `-` items
 * because that structure is the thing downstream needs. SpecGraph admits a
 * statement partly on its structural role — a `LIST_ITEM` under a heading is a
 * requirement candidate where the same words in a paragraph are prose — so
 * flattening a document to unmarked lines throws away exactly the signal the
 * atomiser reads. Tables become pipe rows for the same reason: one row is one
 * statement, and a table dissolved into loose cells is not.
 *
 * ## No dependency, and none wanted
 *
 * Apache POI would do this and would also add ~15 MB and a transitive tree to
 * a jar that has to run on a phone. The subset of OOXML that carries text is
 * small and stable, and reading it directly keeps extraction deterministic:
 * the same bytes give the same markdown on every machine, with no library
 * version able to change that answer between two runs.
 */
object DocxTextExtractor {

    /** @return the document as markdown, or null when it holds no text. */
    fun extract(bytes: ByteArray): String? {
        val xml = entry(bytes, "word/document.xml") ?: return null
        val body = xml.substringAfter("<w:body", missingDelimiterValue = xml)
            .substringBeforeLast("</w:body>")
        return render(body).trim().replace(BLANK_RUN, "\n\n").ifBlank { null }
    }

    private fun entry(bytes: ByteArray, name: String): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == name }
                ?.let { zip.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    /**
     * Walks the body, taking whichever of a table or a paragraph comes next.
     *
     * A linear scan rather than a DOM parse: the file is operator-supplied and
     * may be large, and an XML parser here would also accept a doctype, which
     * is an XXE surface on a path whose whole job is to read a stranger's file.
     */
    private fun render(body: String): String {
        val out = StringBuilder()
        var index = 0

        while (index < body.length) {
            val table = openTag(body, "w:tbl", index)
            val paragraph = openTag(body, "w:p", index)

            if (table >= 0 && (paragraph < 0 || table < paragraph)) {
                val end = body.indexOf("</w:tbl>", table)
                if (end < 0) break
                out.append(renderTable(body.substring(table, end)))
                index = end + "</w:tbl>".length
            } else if (paragraph >= 0) {
                val end = body.indexOf("</w:p>", paragraph)
                if (end < 0) break
                out.append(renderParagraph(body.substring(paragraph, end)))
                index = end + "</w:p>".length
            } else {
                break
            }
        }

        return out.toString()
    }

    private fun renderParagraph(chunk: String): String {
        val properties = chunk.substringAfter("<w:pPr>", "").substringBefore("</w:pPr>")
        val text = runsOf(chunk).trim()
        if (text.isEmpty()) return "\n"

        val heading = HEADING.find(properties)?.groupValues?.get(1)?.toIntOrNull()
        val listed = properties.contains("<w:numPr>")
        val oneLine = text.replace('\n', ' ').replace(SPACE_RUN, " ")

        return when {
            heading != null -> "\n" + "#".repeat(heading.coerceIn(1, 6)) + " " + oneLine + "\n\n"
            listed -> "- $oneLine\n"
            else -> "$text\n"
        }
    }

    /**
     * A table as markdown pipe rows, with a header rule after the first.
     *
     * The rule is emitted unconditionally because markdown needs one to read
     * the block as a table at all, and a docx does not reliably mark which row
     * is the header. Treating the first row as the header is what a reader
     * does anyway.
     */
    private fun renderTable(chunk: String): String = buildString {
        append('\n')
        var index = 0
        var first = true

        while (true) {
            val start = openTag(chunk, "w:tr", index)
            if (start < 0) break
            val end = chunk.indexOf("</w:tr>", start)
            if (end < 0) break

            val cells = cellsOf(chunk.substring(start, end))
            if (cells.isNotEmpty()) {
                append("| ").append(cells.joinToString(" | ")).append(" |\n")
                if (first) {
                    append("|").append(cells.joinToString("|") { " --- " }).append("|\n")
                    first = false
                }
            }
            index = end + "</w:tr>".length
        }
        append('\n')
    }

    private fun cellsOf(row: String): List<String> {
        val cells = mutableListOf<String>()
        var index = 0

        while (true) {
            val start = openTag(row, "w:tc", index)
            if (start < 0) break
            val end = row.indexOf("</w:tc>", start)
            if (end < 0) break
            // A bar inside a cell would end the cell early once this is read
            // back as markdown, so it is escaped rather than passed through.
            cells += runsOf(row.substring(start, end))
                .replace('\n', ' ')
                .replace("|", "\\|")
                .replace(SPACE_RUN, " ")
                .trim()
            index = end + "</w:tc>".length
        }
        return cells
    }

    /** The text a run of `<w:t>`, `<w:tab/>` and `<w:br/>` elements carries. */
    private fun runsOf(chunk: String): String = buildString {
        RUN.findAll(chunk).forEach { match ->
            when {
                match.value.startsWith("<w:tab") -> append(' ')
                match.value.startsWith("<w:br") -> append('\n')
                else -> append(unescape(match.groupValues[1]))
            }
        }
    }

    private fun unescape(value: String): String {
        if ('&' !in value) return value
        return ENTITY.replace(value) { match ->
            when (val name = match.groupValues[1]) {
                "lt" -> "<"
                "gt" -> ">"
                "amp" -> "&"
                "quot" -> "\""
                "apos" -> "'"
                else -> when {
                    name.startsWith("#x") || name.startsWith("#X") ->
                        name.drop(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
                    name.startsWith("#") ->
                        name.drop(1).toIntOrNull()?.let(::codePoint) ?: match.value
                    else -> match.value
                }
            }
        }
    }

    private fun codePoint(value: Int): String =
        if (value in 1..0x10FFFF) String(Character.toChars(value)) else ""

    /**
     * The index of `<name>` or `<name ...>`, skipping longer tags that share
     * the prefix.
     *
     * `<w:p` matches `<w:pPr` on a plain `indexOf`, and taking that as the
     * start of a paragraph swallows the rest of the document.
     */
    private fun openTag(source: String, name: String, from: Int): Int {
        var index = from.coerceAtLeast(0)
        while (index < source.length) {
            val at = source.indexOf("<$name", index)
            if (at < 0) return -1
            val next = source.getOrNull(at + name.length + 1)
            if (next == null || next == '>' || next == '/' || next.isWhitespace()) return at
            index = at + 1
        }
        return -1
    }

    private val RUN = Regex(
        """<w:t(?:\s[^>]*)?>(.*?)</w:t>|<w:tab\s*/>|<w:br\s*/>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val HEADING = Regex("""w:val="[Hh]eading\s*(\d)"""")
    private val ENTITY = Regex("""&([a-zA-Z]+|#\d+|#[xX][0-9a-fA-F]+);""")
    private val SPACE_RUN = Regex(" {2,}")
    private val BLANK_RUN = Regex("\n{3,}")
}
