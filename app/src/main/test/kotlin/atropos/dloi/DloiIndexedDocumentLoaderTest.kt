package atropos.dloi

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DloiIndexedDocumentLoaderTest {
    @Test
    fun loads_indexed_document_with_sections_and_alias_id() {
        val root = Files.createTempDirectory("atropos-dloi-loader-")
        val source = root.resolve("ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt")
        Files.writeString(source, "Phase 11\nSelf-build\n")
        val index = root.resolve("doc.json")
        Files.writeString(
            index,
            """
            {
              "source_id": "97cff09c0f362337",
              "original_filename": "ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt",
              "kind": "text",
              "normalized_path": "${source.toString().replace("\\", "\\\\")}",
              "line_count": 2,
              "page_count": 1,
              "paragraph_count": 1,
              "sections": [
                {
                  "section_id": "S0013",
                  "heading": "Phase 11: Self-Build Loop",
                  "start_line": 1,
                  "end_line": 2,
                  "start_page": 1,
                  "end_page": 1,
                  "start_paragraph": 1,
                  "end_paragraph": 1
                }
              ]
            }
            """.trimIndent()
        )

        val document = DloiIndexedDocumentLoader().load(index)

        assertEquals("authority", document.id)
        assertEquals("97cff09c0f362337", document.sourceId)
        assertEquals(1, document.sections.size)
        assertEquals("S0013", document.sections.single().id)
        assertTrue(DloiAliasResolver().sectionAliases(document.sections.single()).contains("phase_11"))
    }
}
