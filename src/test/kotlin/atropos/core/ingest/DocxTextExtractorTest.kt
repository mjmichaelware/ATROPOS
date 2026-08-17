/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `.docx` mention has to arrive as its text.
 *
 * `MentionResolver` has always accepted `docx`, so `@spec.docx` resolved,
 * passed territory, passed the size ceiling, and the CLI printed
 * `attached: spec.docx` — and then handed the model
 * `(binary docx file; contents not included)`. The operator was told the file
 * was attached and the model was told there had been one. These tests exist so
 * that stays fixed.
 */
class DocxTextExtractorTest {

    private fun docx(body: String): ByteArray {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>$body</w:body></w:document>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun paragraph(text: String, properties: String = "") =
        "<w:p>${if (properties.isEmpty()) "" else "<w:pPr>$properties</w:pPr>"}" +
            "<w:r><w:t>$text</w:t></w:r></w:p>"

    @Test
    fun a_paragraph_becomes_a_line() {
        val text = DocxTextExtractor.extract(docx(paragraph("The engine reads the document.")))

        assertEquals("The engine reads the document.", text)
    }

    @Test
    fun a_heading_becomes_a_markdown_heading() {
        val body = paragraph("Provider discovery", """<w:pStyle w:val="Heading2"/>""") +
            paragraph("Discovery enumerates every configured provider.")

        val text = assertNotNull(DocxTextExtractor.extract(docx(body)))

        assertTrue(
            text.lineSequence().any { it == "## Provider discovery" },
            "heading level was lost; got:\n$text"
        )
    }

    @Test
    fun a_numbered_paragraph_becomes_a_list_item() {
        // The marker matters beyond looks: SpecGraph admits a LIST_ITEM under a
        // heading as a requirement candidate where the same words in a
        // paragraph are prose. A docx flattened to bare lines loses exactly the
        // structure the atomiser reads.
        val body = paragraph("Resolve the provider", """<w:numPr><w:ilvl w:val="0"/></w:numPr>""") +
            paragraph("Record the evidence", """<w:numPr><w:ilvl w:val="0"/></w:numPr>""")

        val text = assertNotNull(DocxTextExtractor.extract(docx(body)))

        assertEquals(
            listOf("- Resolve the provider", "- Record the evidence"),
            text.lines().filter { it.startsWith("- ") }
        )
    }

    @Test
    fun a_table_becomes_pipe_rows_with_a_header_rule() {
        val row = { cells: List<String> ->
            "<w:tr>" + cells.joinToString("") { "<w:tc><w:p><w:r><w:t>$it</w:t></w:r></w:p></w:tc>" } + "</w:tr>"
        }
        val body = "<w:tbl>" + row(listOf("Atom", "Owner")) + row(listOf("B-MCP-GH-a", "core")) + "</w:tbl>"

        val text = assertNotNull(DocxTextExtractor.extract(docx(body)))
        val rows = text.lines().filter { it.startsWith("|") }

        assertEquals(
            listOf("| Atom | Owner |", "| --- | --- |", "| B-MCP-GH-a | core |"),
            rows
        )
    }

    @Test
    fun xml_entities_are_decoded() {
        val body = paragraph("IDs are &lt;SYS&gt; scoped &amp; unique")

        assertEquals("IDs are <SYS> scoped & unique", DocxTextExtractor.extract(docx(body)))
    }

    @Test
    fun paragraph_properties_are_not_mistaken_for_a_paragraph() {
        // `<w:p` is a prefix of `<w:pPr`. Matching on the prefix alone made the
        // scanner start a paragraph inside a properties block and swallow the
        // rest of the document.
        val body = paragraph("First", """<w:pStyle w:val="Normal"/>""") + paragraph("Second")

        val text = assertNotNull(DocxTextExtractor.extract(docx(body)))

        assertTrue(text.contains("Second"), "the scan stopped early; got:\n$text")
    }

    @Test
    fun a_file_that_is_not_a_docx_is_refused_rather_than_guessed() {
        assertNull(DocxTextExtractor.extract("not a zip".toByteArray()))
    }

    @Test
    fun an_empty_document_reports_nothing_rather_than_blank_text() {
        assertNull(DocxTextExtractor.extract(docx("")))
    }

    @Test
    fun the_reader_delivers_docx_text_to_the_prompt() {
        // The end of the chain, which is where the defect was visible: the
        // extractor can be right and the operator still get nothing if the
        // reader never calls it.
        val bytes = docx(paragraph("One atom is one acceptance predicate."))
        val reader = AttachmentReader(readBytes = { bytes })

        val attachment = assertNotNull(
            reader.read(MentionResolution.Resolved(Path.of("spec.docx"), "docx"))
        )

        assertTrue(attachment.isText, "a docx still arrived as opaque bytes")
        assertTrue(
            attachment.promptBlock().contains("One atom is one acceptance predicate."),
            "the document did not reach the prompt block"
        )
    }
}
