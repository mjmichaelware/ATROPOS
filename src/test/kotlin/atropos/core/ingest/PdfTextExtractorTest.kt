/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `.pdf` mention has to arrive as its text, or say plainly that it cannot.
 *
 * The second half matters as much as the first. A PDF that encodes glyphs
 * through a custom map decodes to characters that are valid and meaningless,
 * and handing those to the atomiser would turn mojibake into requirements with
 * every appearance of success.
 */
class PdfTextExtractorTest {

    /** A minimal PDF whose single content stream is [content]. */
    private fun pdf(content: String, compress: Boolean = false): ByteArray {
        val payload =
            if (compress) {
                val deflater = Deflater()
                deflater.setInput(content.toByteArray())
                deflater.finish()
                val buffer = ByteArray(64 * 1024)
                val produced = deflater.deflate(buffer)
                deflater.end()
                buffer.copyOf(produced)
            } else {
                content.toByteArray()
            }

        val out = ByteArrayOutputStream()
        out.write("%PDF-1.4\n1 0 obj\n<< /Length ${payload.size}".toByteArray())
        if (compress) out.write(" /Filter /FlateDecode".toByteArray())
        out.write(" >>\nstream\n".toByteArray())
        out.write(payload)
        out.write("\nendstream\nendobj\n%%EOF\n".toByteArray())
        return out.toByteArray()
    }

    @Test
    fun a_text_showing_operator_becomes_text() {
        val text = PdfTextExtractor.extract(
            pdf("BT /F1 12 Tf 72 720 Td (One atom is one acceptance predicate.) Tj ET")
        )

        assertEquals_contains(text, "One atom is one acceptance predicate.")
    }

    @Test
    fun a_deflated_stream_is_inflated_first() {
        // Practically every real PDF compresses its content streams, so an
        // extractor that only handled the uncompressed case would work on
        // nothing an operator actually has.
        val text = PdfTextExtractor.extract(
            pdf("BT (Provider discovery enumerates every configured provider.) Tj ET", compress = true)
        )

        assertEquals_contains(text, "Provider discovery enumerates every configured provider.")
    }

    @Test
    fun a_hex_string_is_decoded() {
        val text = PdfTextExtractor.extract(
            pdf("BT <54686520656E67696E652072656164732074686520646F63756D656E742E> Tj ET")
        )

        assertEquals_contains(text, "The engine reads the document.")
    }

    @Test
    fun escapes_and_nesting_inside_a_literal_survive() {
        val text = PdfTextExtractor.extract(
            pdf("""BT (Atoms \(and their dimensions\) are recorded verbatim.) Tj ET""")
        )

        assertEquals_contains(text, "Atoms (and their dimensions) are recorded verbatim.")
    }

    @Test
    fun a_wide_kerning_gap_becomes_the_space_it_stands_for() {
        // `[(Pro)-278(vider)]` holds no space character; the number is the
        // space. Without this the whole document arrives as one run-on word.
        val text = assertNotNull(
            PdfTextExtractor.extract(
                pdf("BT [(Deterministic)-500(atomisation)-500(of)-500(any)-500(document)] TJ ET")
            )
        )

        assertTrue(
            text.contains("Deterministic atomisation"),
            "words were welded together: $text"
        )
    }

    @Test
    fun a_line_moving_operator_ends_the_line() {
        val text = assertNotNull(
            PdfTextExtractor.extract(
                pdf("BT (First requirement.) Tj 0 -14 Td (Second requirement.) Tj ET")
            )
        )

        assertTrue(
            text.lines().size >= 2,
            "the whole page collapsed onto one line: $text"
        )
    }

    @Test
    fun a_file_that_is_not_a_pdf_is_refused_rather_than_guessed() {
        assertNull(PdfTextExtractor.extract("not a pdf".toByteArray()))
        assertNull(PdfTextExtractor.extract(ByteArray(0)))
    }

    @Test
    fun glyph_indices_are_reported_unreadable_rather_than_delivered_as_prose() {
        // This is the case the whole readability check exists for. A custom
        // font encoding decodes to bytes that are valid characters and mean
        // nothing, and "confident rubbish" is worse than "cannot read this"
        // precisely because nothing downstream would notice.
        val garbage = "#%&*+=~^".repeat(60)

        assertNull(PdfTextExtractor.extract(pdf("BT ($garbage) Tj ET")))
    }

    @Test
    fun the_reader_delivers_pdf_text_to_the_prompt() {
        val bytes = pdf("BT (Grand total order is roughly four hundred atoms.) Tj ET")
        val reader = AttachmentReader(readBytes = { bytes })

        val attachment = assertNotNull(
            reader.read(MentionResolution.Resolved(Path.of("spec.pdf"), "pdf"))
        )

        assertTrue(attachment.isText, "a pdf still arrived as opaque bytes")
        assertTrue(
            attachment.promptBlock().contains("roughly four hundred atoms"),
            "the document did not reach the prompt block"
        )
    }

    @Test
    fun an_unreadable_pdf_still_attaches_and_says_it_could_not_be_read() {
        // Refusing the attachment outright would tell the operator the file
        // does not exist. It exists; its text does not.
        val reader = AttachmentReader(readBytes = { "%PDF-1.4\nno streams here\n".toByteArray() })

        val attachment = assertNotNull(
            reader.read(MentionResolution.Resolved(Path.of("scan.pdf"), "pdf"))
        )

        assertTrue(!attachment.isText)
        assertTrue(attachment.promptBlock().contains("contents not included"))
    }

    private fun assertEquals_contains(text: String?, expected: String) {
        val actual = assertNotNull(text, "nothing was extracted")
        assertTrue(actual.contains(expected), "expected '$expected' in:\n$actual")
    }
}
