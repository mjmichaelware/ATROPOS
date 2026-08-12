package atropos.dloi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Path

class DloiServiceTest {
    @Test
    fun lookup_resolves_exact_authority_excerpt() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.lookup("authority#phase_1")
        assertEquals("authority", result.document.id)
        assertEquals("97cff09c0f362337", result.document.sourceId)
        assertEquals("S0003", result.coordinate.sectionId)
        assertTrue(result.excerpt.contains("Provider Activation Doctor"))
    }

    @Test
    fun resolve_task_proves_section_from_authority_doc() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.resolveTask("Phase 7 AST Symbol Graph")
        assertEquals("97cff09c0f362337", result.coordinate.sourceId)
        assertEquals("S0009", result.coordinate.sectionId)
        assertTrue(result.excerpt.contains("AST Symbol Graph"))
    }

    @Test
    fun lookup_preserves_exact_line_coordinates_and_provenance() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.lookup("97cff09c0f362337#S0008@L31-33")

        assertEquals("authority", result.coordinate.documentId)
        assertEquals("97cff09c0f362337", result.coordinate.sourceId)
        assertEquals("S0008", result.coordinate.sectionId)
        assertEquals(31, result.coordinate.lineStart)
        assertEquals(33, result.coordinate.lineEnd)
        assertEquals(1, result.coordinate.pageStart)
        assertEquals(1, result.coordinate.pageEnd)
        assertEquals(11, result.coordinate.paragraphStart)
        assertEquals(11, result.coordinate.paragraphEnd)
        assertTrue(result.provenance.contains("source=97cff09c0f362337"))
        assertTrue(result.provenance.contains("section=S0008"))
        assertTrue(result.provenance.contains("lines=31-33"))
        assertTrue(result.excerpt.contains("DLOI Source Router"))
    }

    @Test
    fun lookup_refuses_unproven_section_instead_of_blind_ingestion() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())

        assertFailsWith<IllegalStateException> {
            service.lookup("authority#does_not_exist")
        }
    }

    @Test
    fun lookup_supports_paragraph_selectors_from_indexed_authority() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.lookup("authority#S0008@PARA11-11")

        assertEquals("97cff09c0f362337", result.coordinate.sourceId)
        assertEquals("S0008", result.coordinate.sectionId)
        assertEquals(31, result.coordinate.lineStart)
        assertEquals(33, result.coordinate.lineEnd)
        assertEquals(11, result.coordinate.paragraphStart)
        assertEquals(11, result.coordinate.paragraphEnd)
        assertTrue(result.excerpt.contains("source docs become addressable machine truth"))
    }
}
