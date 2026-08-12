/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Priority #6 — Source Authority Law runtime enforcement tests.
 *
 * These tests verify the law that governs exact source authority:
 * - Hash-pinned integrity against real files
 * - Index freshness detection
 * - HIG=0 composition (no guessed results from unverified sources)
 */
class SourceAuthorityLawTest {

    @Test
    fun verify_reports_no_sources_when_docs_source_missing() {
        val repoRoot = Files.createTempDirectory("atropos-law-nosrc-")
        val law = SourceAuthorityLaw(repoRoot)
        val verdict = law.verify()

        assertTrue(verdict is SourceAuthorityLaw.SourceAuthorityVerdict.NoSources)
        assertTrue((verdict as SourceAuthorityLaw.SourceAuthorityVerdict.NoSources).reason.contains("does not exist"))
    }

    @Test
    fun verify_reports_no_sources_when_docs_source_is_empty() {
        val repoRoot = Files.createTempDirectory("atropos-law-empty-")
        Files.createDirectories(repoRoot.resolve("docs/source"))
        val law = SourceAuthorityLaw(repoRoot)
        val verdict = law.verify()

        assertTrue(verdict is SourceAuthorityLaw.SourceAuthorityVerdict.NoSources)
    }

    @Test
    fun verify_reports_unindexed_files() {
        val repoRoot = Files.createTempDirectory("atropos-law-unindexed-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)
        Files.writeString(
            sourceDir.resolve("test.txt"),
            "Test authority document content\n",
            StandardCharsets.UTF_8
        )

        val law = SourceAuthorityLaw(repoRoot)
        val verdict = law.verify()

        assertTrue(verdict is SourceAuthorityLaw.SourceAuthorityVerdict.Verified)
        val verified = verdict as SourceAuthorityLaw.SourceAuthorityVerdict.Verified
        assertEquals(0, verified.verifiedDocuments.size, "no documents indexed yet")
        assertEquals(1, verified.unindexedFiles.size, "one file not yet indexed")
    }

    @Test
    fun ensure_index_builds_and_verifies_from_scratch() {
        val repoRoot = Files.createTempDirectory("atropos-law-ensure-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = "Phase 0: Baseline Lock\nFreeze current truth.\n"
        Files.writeString(sourceDir.resolve("test_doc.txt"), content, StandardCharsets.UTF_8)

        val law = SourceAuthorityLaw(repoRoot)
        val verdict = law.ensureIndex()

        assertTrue(
            verdict is SourceAuthorityLaw.SourceAuthorityVerdict.Verified,
            "ensureIndex must produce Verified after indexing: $verdict"
        )
        val verified = verdict as SourceAuthorityLaw.SourceAuthorityVerdict.Verified
        assertTrue(verified.verifiedDocuments.isNotEmpty(), "at least one doc should be verified")
        assertTrue(verified.unindexedFiles.isEmpty(), "no files should remain unindexed")
    }

    @Test
    fun verify_detects_modified_source_file() {
        val repoRoot = Files.createTempDirectory("atropos-law-modified-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val originalContent = "Phase 0: Baseline Lock\nFreeze current truth.\n"
        val sourcePath = sourceDir.resolve("test_doc.txt")
        Files.writeString(sourcePath, originalContent, StandardCharsets.UTF_8)

        // Index the original
        val indexer = SourceAuthorityIndexer(repoRoot)
        indexer.indexFile(sourcePath)

        // Verify should pass
        val law = SourceAuthorityLaw(repoRoot)
        val v1 = law.verify()
        assertTrue(v1 is SourceAuthorityLaw.SourceAuthorityVerdict.Verified, "initial verify should pass")

        // Now modify the source file
        Files.writeString(sourcePath, "MODIFIED CONTENT\nDifferent text.\n", StandardCharsets.UTF_8)

        // A changed byte is a hash mismatch, not a new authority document.
        val v2 = law.verify()
        assertTrue(
            v2 is SourceAuthorityLaw.SourceAuthorityVerdict.Rejected,
            "modified file must be rejected under hash-pinned authority: $v2"
        )
        val rejected = v2 as SourceAuthorityLaw.SourceAuthorityVerdict.Rejected
        assertTrue(rejected.mismatches.any { it.filename == "test_doc.txt" })
    }

    @Test
    fun guarded_resolve_returns_no_match_when_no_sources() {
        val repoRoot = Files.createTempDirectory("atropos-law-guard-nosrc-")
        val law = SourceAuthorityLaw(repoRoot)
        val service = DloiService(repoRoot)
        val guard = HigZeroGuard(service)

        val result = law.guardedResolve(guard, "authority#phase_1")

        assertTrue(result is DloiLookupResult.NoMatch, "must be NoMatch when no sources exist")
    }

    @Test
    fun guarded_resolve_succeeds_after_ensure_index() {
        val repoRoot = Files.createTempDirectory("atropos-law-guard-ok-")
        val sourceDir = repoRoot.resolve("docs/source")
        Files.createDirectories(sourceDir)

        val content = listOf(
            "AUTHORITY DOC",
            "",
            "",
            "Phase 1: Provider Activation Doctor",
            "Make providers real."
        ).joinToString("\n")
        Files.writeString(
            sourceDir.resolve("ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt"),
            content,
            StandardCharsets.UTF_8
        )

        val law = SourceAuthorityLaw(repoRoot)
        // First ensure the index is built
        law.ensureIndex()

        val service = DloiService(repoRoot)
        val guard = HigZeroGuard(service)
        val result = law.guardedResolve(guard, "authority#phase_1")

        assertTrue(
            result is DloiLookupResult.Resolved,
            "guarded resolve should succeed after ensureIndex: ${(result as? DloiLookupResult.NoMatch)?.reason}"
        )
    }

    @Test
    fun real_repo_verify_passes_if_docs_source_exists() {
        // Run against the real repo only if docs/source/ exists
        val repoRoot = Path.of(".").toAbsolutePath().normalize()
        val sourceDir = repoRoot.resolve("docs/source")
        if (!Files.exists(sourceDir)) return

        val law = SourceAuthorityLaw(repoRoot)
        val verdict = law.verify()

        assertTrue(
            verdict !is SourceAuthorityLaw.SourceAuthorityVerdict.Rejected,
            "real repo source authority must not be rejected: $verdict"
        )
    }
}
