package atropos.dloi

data class DloiLineRecord(
    val number: Int,
    val text: String,
    val page: Int?,
    val paragraph: Int?
)

class DloiLineIndexer {
    fun index(document: DloiDocument, lines: List<String>): List<DloiLineRecord> =
        when (document.kind.lowercase()) {
            "pdf" -> pdfLines(lines.joinToString("\n"))
            "docx" -> docxLines(lines)
            else -> plainLines(lines)
        }

    private fun plainLines(lines: List<String>): List<DloiLineRecord> {
        val records = mutableListOf<DloiLineRecord>()
        var paragraph = 0
        var inParagraph = false
        lines.forEachIndexed { index, line ->
            val hasContent = line.isNotBlank()
            if (hasContent && !inParagraph) {
                paragraph += 1
                inParagraph = true
            } else if (!hasContent) {
                inParagraph = false
            }
            records += DloiLineRecord(
                number = index + 1,
                text = line,
                page = 1,
                // Blank lines after a paragraph remain part of that paragraph's address span.
                paragraph = if (paragraph == 0) null else paragraph
            )
        }
        return records
    }

    private fun docxLines(lines: List<String>): List<DloiLineRecord> =
        lines.mapIndexed { index, line ->
            DloiLineRecord(
                number = index + 1,
                text = line,
                page = null,
                paragraph = index + 1
            )
        }

    private fun pdfLines(text: String): List<DloiLineRecord> {
        val records = mutableListOf<DloiLineRecord>()
        var globalLine = 0
        var paragraph = 0
        text.split('\u000C').forEachIndexed { pageIndex, pageText ->
            var inParagraph = false
            pageText.split('\n').forEach { line ->
                globalLine += 1
                val hasContent = line.isNotBlank()
                if (hasContent && !inParagraph) {
                    paragraph += 1
                    inParagraph = true
                } else if (!hasContent) {
                    inParagraph = false
                }
                records += DloiLineRecord(
                    number = globalLine,
                    text = line,
                    page = pageIndex + 1,
                    paragraph = if (hasContent) paragraph else null
                )
            }
        }
        return records
    }
}
