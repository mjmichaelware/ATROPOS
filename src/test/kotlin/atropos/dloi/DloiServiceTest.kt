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
        assertTrue(result.excerpt.contains("Provider Activation Doctor"))
    }

    @Test
    fun resolve_task_proves_section_from_authority_doc() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.resolveTask("Phase 7 AST Symbol Graph")
        assertTrue(result.excerpt.contains("AST Symbol Graph"))
    }

    @Test
    fun lookup_preserves_exact_line_coordinates_and_provenance() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())
        val result = service.lookup("authority#phase_6@L259-264")

        assertEquals("authority", result.coordinate.documentId)
        assertEquals("phase_6", result.coordinate.sectionId)
        assertEquals(259, result.coordinate.lineStart)
        assertEquals(264, result.coordinate.lineEnd)
        assertTrue(result.provenance.endsWith("docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md:259-264"))
        assertTrue(result.excerpt.contains("DLOI Source Router"))
    }

    @Test
    fun lookup_refuses_unproven_section_instead_of_blind_ingestion() {
        val service = DloiService(Path.of(".").toAbsolutePath().normalize())

        assertFailsWith<IllegalStateException> {
            service.lookup("authority#does_not_exist")
        }
    }
}
