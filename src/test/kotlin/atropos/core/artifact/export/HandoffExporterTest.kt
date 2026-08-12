/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SUP.ART.HANDOFF-EXPORT` and `SUP.ART.ROOT-OR-DOWNLOADS`: an artifact leaves
 * the system only through an explicit, territory-bounded, redacted channel.
 */
class HandoffExporterTest {

    private fun repo(): Path = Files.createTempDirectory("atropos-export-test")

    private fun exporterFor(repo: Path, downloads: Path? = null) = HandoffExporter(
        resolver = ArtifactLandingResolver(repo, downloads),
        clock = { Instant.parse("2026-08-12T09:00:00Z") }
    )

    @Test
    fun `an export lands under the repository by default`() {
        val repo = repo()

        val result = exporterFor(repo).export(
            HandoffType.REPORT,
            ArtifactLanding.RepositoryRoot,
            listOf(repo)
        ) { "the run finished" }

        assertTrue(result is ExportResult.Written)
        assertTrue(result.path.startsWith(repo))
        assertEquals("repository", result.zone)
        assertTrue(Files.readString(result.path).contains("the run finished"))
    }

    @Test
    fun `the filename comes from the type and the clock, never from input`() {
        val repo = repo()

        val result = exporterFor(repo).export(
            HandoffType.HANDOFF,
            ArtifactLanding.RepositoryRoot,
            listOf(repo)
        ) { "body" }

        assertEquals(
            "atropos-handoff-20260812-090000.md",
            (result as ExportResult.Written).path.fileName.toString()
        )
    }

    @Test
    fun `an explicit path outside the granted territory is refused`() {
        val repo = repo()
        val elsewhere = Files.createTempDirectory("atropos-export-outside")

        val result = exporterFor(repo).export(
            HandoffType.REPORT,
            ArtifactLanding.Explicit(elsewhere),
            listOf(repo)
        ) { "secret plans" }

        assertFalse(result.ok)
        assertTrue((result as ExportResult.Refused).reason.contains("outside the territory"))
        assertEquals(0, Files.list(elsewhere).use { it.count() })
    }

    @Test
    fun `an explicit path inside the granted territory is written`() {
        val repo = repo()
        val inside = repo.resolve("out/here")

        val result = exporterFor(repo).export(
            HandoffType.REPORT,
            ArtifactLanding.Explicit(inside),
            listOf(repo)
        ) { "body" }

        assertTrue(result is ExportResult.Written)
        assertEquals("explicit", result.zone)
        assertTrue(Files.isRegularFile(result.path))
    }

    @Test
    fun `downloads is refused rather than silently redirected when unavailable`() {
        val repo = repo()

        val result = exporterFor(repo, downloads = null).export(
            HandoffType.REPORT,
            ArtifactLanding.PlatformDownloads,
            listOf(repo)
        ) { "body" }

        assertFalse(result.ok)
        assertTrue((result as ExportResult.Refused).remedy.contains("repository root"))
    }

    @Test
    fun `downloads is used when the platform has one`() {
        val repo = repo()
        val downloads = Files.createTempDirectory("atropos-export-downloads")

        val result = exporterFor(repo, downloads).export(
            HandoffType.REPORT,
            ArtifactLanding.PlatformDownloads,
            listOf(repo)
        ) { "body" }

        assertTrue(result is ExportResult.Written)
        assertEquals("downloads", result.zone)
        assertTrue(result.path.startsWith(downloads))
    }

    @Test
    fun `content is redacted on the way out`() {
        val repo = repo()

        val result = exporterFor(repo).export(
            HandoffType.REPORT,
            ArtifactLanding.RepositoryRoot,
            listOf(repo)
        ) { "token sk-ant-api03-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA and more" }

        val written = Files.readString((result as ExportResult.Written).path)
        assertFalse(
            written.contains("sk-ant-api03-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
            "an exported file is exactly the kind that ends up in a chat window"
        )
    }

    @Test
    fun `every export states what it is and when it was produced`() {
        val repo = repo()

        val result = exporterFor(repo).export(
            HandoffType.EVIDENCE,
            ArtifactLanding.RepositoryRoot,
            listOf(repo)
        ) { "bundles: 3" }

        val written = Files.readString((result as ExportResult.Written).path)
        assertTrue(written.startsWith("# ATROPOS evidence"))
        assertTrue(written.contains("2026-08-12T09:00:00Z"))
    }

    @Test
    fun `an unknown export type resolves to null rather than a default`() {
        assertNull(HandoffType.fromCanonical("../../etc/passwd"))
        assertEquals(HandoffType.SWARM, HandoffType.fromCanonical(" Swarm "))
    }

    @Test
    fun `the downloads locator returns null rather than a path it has not checked`() {
        val located = PlatformDownloadsLocator.locate { name ->
            if (name == "XDG_DOWNLOAD_DIR") "/definitely/not/a/real/directory" else null
        }

        assertTrue(located == null || Files.isWritable(located))
    }

    @Test
    fun `an explicitly declared downloads directory is preferred`() {
        val declared = Files.createTempDirectory("atropos-declared-downloads")

        val located = PlatformDownloadsLocator.locate { name ->
            if (name == "XDG_DOWNLOAD_DIR") declared.toString() else null
        }

        assertEquals(declared, located)
    }
}
