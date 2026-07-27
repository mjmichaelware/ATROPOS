package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DloiTaskResolverTest {
    @Test
    fun resolve_task_uses_explicit_section_id_without_guessing() {
        val repoRoot = Files.createTempDirectory("atropos-dloi-explicit-")
        writeIndexedDocument(
            repoRoot = repoRoot,
            sha = "a".repeat(64),
            sourceId = "97cff09c0f362337",
            originalFilename = "97cff09c0f362337__ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt",
            text = listOf(
                "Phase 6: DLOI Source Router",
                "Source documents become exact machine-addressable truth."
            ),
            sections = listOf(
                SectionFixture("S0008", "Phase 6: DLOI Source Router", 1, 2, 1, 1)
            )
        )

        val service = DloiService(repoRoot)
        val result = service.resolveTask("Use 97cff09c0f362337 S0008 for the active requirement")

        assertEquals("97cff09c0f362337", result.coordinate.sourceId)
        assertEquals("S0008", result.coordinate.sectionId)
    }

    @Test
    fun resolve_task_refuses_ambiguous_authority_matches() {
        val repoRoot = Files.createTempDirectory("atropos-dloi-ambiguous-")
        writeIndexedDocument(
            repoRoot = repoRoot,
            sha = "b".repeat(64),
            sourceId = "source-one",
            originalFilename = "source-one__MAINTENANCE_ONE.md",
            text = listOf("Maintenance section one"),
            sections = listOf(SectionFixture("S0001", "Maintenance", 1, 1, 1, 1))
        )
        writeIndexedDocument(
            repoRoot = repoRoot,
            sha = "c".repeat(64),
            sourceId = "source-two",
            originalFilename = "source-two__MAINTENANCE_TWO.md",
            text = listOf("Maintenance section two"),
            sections = listOf(SectionFixture("S0002", "Maintenance", 1, 1, 1, 1))
        )

        val service = DloiService(repoRoot)
        val failure = assertFailsWith<IllegalArgumentException> {
            service.resolveTask("maintenance")
        }

        assertEquals(
            "ambiguous authoritative source section for task: source-one#S0001, source-two#S0002",
            failure.message
        )
    }

    @Test
    fun resolve_task_uses_document_alias_to_break_ties() {
        val repoRoot = Files.createTempDirectory("atropos-dloi-closure-")
        writeIndexedDocument(
            repoRoot = repoRoot,
            sha = "d".repeat(64),
            sourceId = "authority-source",
            originalFilename = "authority-source__ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt",
            text = listOf("Maintenance authority section"),
            sections = listOf(SectionFixture("S0011", "Maintenance", 1, 1, 1, 1))
        )
        writeIndexedDocument(
            repoRoot = repoRoot,
            sha = "e".repeat(64),
            sourceId = "closure-source",
            originalFilename = "closure-source__ATROPOS_CANONICAL_PHASES_1_11_CLOSURE.md",
            text = listOf("Maintenance closure section"),
            sections = listOf(SectionFixture("S0099", "Maintenance", 1, 1, 1, 1))
        )

        val service = DloiService(repoRoot)
        val result = service.resolveTask("closure maintenance")

        assertEquals("closure-source", result.coordinate.sourceId)
        assertEquals("S0099", result.coordinate.sectionId)
    }

    private data class SectionFixture(
        val id: String,
        val heading: String,
        val startLine: Int,
        val endLine: Int,
        val startParagraph: Int,
        val endParagraph: Int
    )

    private fun writeIndexedDocument(
        repoRoot: Path,
        sha: String,
        sourceId: String,
        originalFilename: String,
        text: List<String>,
        sections: List<SectionFixture>
    ) {
        val indexRoot = repoRoot.resolve(".atropos/context-cache/source-index/v1")
        val normalizedPath = indexRoot.resolve("normalized/${sha.take(2)}/$sha.v1.txt")
        val extractedPath = indexRoot.resolve("extracted/${sha.take(2)}/$sha.v1.json")
        Files.createDirectories(normalizedPath.parent)
        Files.createDirectories(extractedPath.parent)
        Files.writeString(normalizedPath, text.joinToString("\n") + "\n", StandardCharsets.UTF_8)
        Files.writeString(
            extractedPath,
            buildJson(sourceId, originalFilename, normalizedPath, text.size, sections),
            StandardCharsets.UTF_8
        )
    }

    private fun buildJson(
        sourceId: String,
        originalFilename: String,
        normalizedPath: Path,
        lineCount: Int,
        sections: List<SectionFixture>
    ): String = buildString {
        appendLine("{")
        appendLine("""  "source_id": "$sourceId",""")
        appendLine("""  "original_filename": "$originalFilename",""")
        appendLine("""  "kind": "text",""")
        appendLine("""  "normalized_path": "${normalizedPath.toAbsolutePath().normalize()}",""")
        appendLine("""  "line_count": $lineCount,""")
        appendLine("""  "page_count": 1,""")
        appendLine("""  "paragraph_count": ${sections.maxOfOrNull { it.endParagraph } ?: 0},""")
        appendLine("""  "sections": [""")
        sections.forEachIndexed { index, section ->
            appendLine("    {")
            appendLine("""      "section_id": "${section.id}",""")
            appendLine("""      "heading": "${section.heading}",""")
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
}
