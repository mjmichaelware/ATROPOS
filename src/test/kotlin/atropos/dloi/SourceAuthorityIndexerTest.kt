/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Priority #6 — Source Authority Indexer builds real indexes from real files.
 *
 * These tests verify the indexer produces the exact JSON shape that
 * [DloiService.loadDocuments] already parses, using real SHA-256 hashes
 * from real file content — no fabricated hashes, no stubs.
 */
class SourceAuthorityIndexerTest {

    @Test
    fun indexer_produces_correct_source_id_from_sha256() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-hash-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = "Phase 1: Provider Activation Doctor\nGoal: make providers real.\n"
        val sourcePath = sourceDir.resolve("test_doc.txt")
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8)

        val expectedSha = SourceAuthorityIndexer.sha256Hex(content.toByteArray(StandardCharsets.UTF_8))
        val expectedSourceId = expectedSha.take(16)

        val indexer = SourceAuthorityIndexer(repoRoot)
        val result = indexer.indexFile(sourcePath)

        assertTrue(result != null, "indexFile must return a result for valid text")
        assertEquals(expectedSha, result!!.sha256, "SHA-256 must match real file content")
        assertEquals(expectedSourceId, result.sourceId, "source_id must be first 16 hex of SHA-256")
    }

    @Test
    fun indexer_detects_phase_headings_as_sections() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-sections-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = listOf(
            "ATROPOS BLUEPRINT",
            "",
            "",
            "Phase 0: Baseline Lock",
            "",
            "",
            "Phase 1: Provider Activation Doctor",
            "",
            "",
            "Phase 2: Provider Transport Completion"
        ).joinToString("\n")

        val sourcePath = sourceDir.resolve("blueprint.txt")
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8)

        val indexer = SourceAuthorityIndexer(repoRoot)
        val lines = content.lines()
        val sections = indexer.detectSections(lines)

        // Should detect: title + 3 phase headings = 4 sections
        assertEquals(4, sections.size, "expected 4 sections (title + 3 phases)")
        assertEquals("S0001", sections[0].sectionId)
        assertEquals("ATROPOS BLUEPRINT", sections[0].heading)
        assertEquals("S0002", sections[1].sectionId)
        assertTrue(sections[1].heading.contains("Phase 0"), "S0002 should be Phase 0")
        assertEquals("S0003", sections[2].sectionId)
        assertTrue(sections[2].heading.contains("Phase 1"), "S0003 should be Phase 1")
        assertEquals("S0004", sections[3].sectionId)
        assertTrue(sections[3].heading.contains("Phase 2"), "S0004 should be Phase 2")
    }

    @Test
    fun indexed_documents_are_loadable_by_dloi_service() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-load-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = listOf(
            "TEST AUTHORITY DOCUMENT",
            "",
            "",
            "Phase 0: Baseline Lock",
            "Freeze current truth.",
            "",
            "",
            "Phase 1: Provider Activation Doctor",
            "Make providers real and diagnosable."
        ).joinToString("\n")

        val sourcePath = sourceDir.resolve("test_authority.txt")
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8)

        val indexer = SourceAuthorityIndexer(repoRoot)
        val indexed = indexer.indexFile(sourcePath)
        assertTrue(indexed != null)

        // Now DloiService should be able to load it
        val service = DloiService(repoRoot)
        val docs = service.loadDocuments()

        assertTrue(docs.isNotEmpty(), "DloiService must load indexed documents")
        val doc = docs.first()
        assertEquals(indexed!!.sourceId, doc.sourceId, "source_id must match")
        assertTrue(doc.sections.isNotEmpty(), "sections must be populated")
    }

    @Test
    fun indexed_documents_support_exact_address_lookup() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-lookup-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = listOf(
            "AUTHORITY DOC",
            "",
            "",
            "Phase 1: Provider Activation Doctor",
            "Make providers real and diagnosable."
        ).joinToString("\n")

        val sourcePath = sourceDir.resolve("ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt")
        Files.writeString(sourcePath, content, StandardCharsets.UTF_8)

        val indexer = SourceAuthorityIndexer(repoRoot)
        indexer.indexFile(sourcePath)

        val service = DloiService(repoRoot)
        val guard = HigZeroGuard(service)
        // This file's slug will contain "codex_cli_build_blueprint_over_time" so
        // it should be aliased as "authority"
        val result = guard.resolve("authority#phase_1")

        assertTrue(
            result is DloiLookupResult.Resolved,
            "exact address lookup against indexed doc must resolve, got: ${(result as? DloiLookupResult.NoMatch)?.reason}"
        )
    }

    @Test
    fun indexer_skips_empty_files() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-empty-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val sourcePath = sourceDir.resolve("empty.txt")
        Files.writeString(sourcePath, "", StandardCharsets.UTF_8)

        val indexer = SourceAuthorityIndexer(repoRoot)
        val result = indexer.indexFile(sourcePath)

        assertEquals(null, result, "empty files must be skipped")
    }

    @Test
    fun indexer_handles_nonexistent_source_dir() {
        val repoRoot = Files.createTempDirectory("atropos-indexer-missing-")
        val indexer = SourceAuthorityIndexer(repoRoot)
        val results = indexer.index()

        assertTrue(results.isEmpty(), "must return empty for nonexistent docs/source/")
    }

    @Test
    fun sha256_matches_real_codex_cli_blueprint() {
        // Verify the real file hash if it exists in this repo
        val repoRoot = Path.of(".").toAbsolutePath().normalize()
        val blueprintPath = repoRoot.resolve("docs/source/ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt")

        if (Files.exists(blueprintPath)) {
            val bytes = Files.readAllBytes(blueprintPath)
            val sha256 = SourceAuthorityIndexer.sha256Hex(bytes)
            val sourceId = sha256.take(16)

            assertEquals(
                "97cff09c0f362337",
                sourceId,
                "real CODEX-CLI blueprint must hash to 97cff09c0f362337"
            )
        }
        // If file doesn't exist (e.g., fresh clone without docs/source/), skip silently
    }
}
