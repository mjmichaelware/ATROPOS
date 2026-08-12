/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactLandingTest {

    private val repo = Path.of("/work/atropos")
    private val downloads = Path.of("/storage/Download")
    private val granted = listOf(Path.of("/work/atropos"))

    private fun resolver(withDownloads: Boolean = true) =
        ArtifactLandingResolver(repo, if (withDownloads) downloads else null)

    @Test
    fun `the repository zone lands under a declared export directory`() {
        val resolution = resolver().resolve(ArtifactLanding.RepositoryRoot, granted)
        val resolved = resolution as LandingResolution.Resolved
        assertTrue(resolved.directory.toString().contains(".atropos/exports"))
        assertEquals("repository", resolved.zone)
    }

    @Test
    fun `downloads resolves when the platform has one`() {
        val resolved = resolver().resolve(ArtifactLanding.PlatformDownloads, granted)
        assertEquals(downloads, (resolved as LandingResolution.Resolved).directory)
    }

    @Test
    fun `a platform without downloads refuses instead of falling back`() {
        val refused = resolver(withDownloads = false)
            .resolve(ArtifactLanding.PlatformDownloads, granted) as LandingResolution.Refused
        // Quietly writing into the repo would be the surprise write this atom removes.
        assertTrue(refused.reason.contains("no downloads directory"))
        assertTrue(refused.remedy.isNotBlank())
    }

    @Test
    fun `an explicit path inside the grant is allowed`() {
        val resolution = resolver().resolve(
            ArtifactLanding.Explicit(Path.of("/work/atropos/out")), granted
        )
        assertTrue(resolution is LandingResolution.Resolved)
    }

    @Test
    fun `an explicit path outside the grant is refused`() {
        val refused = resolver().resolve(
            ArtifactLanding.Explicit(Path.of("/etc")), granted
        ) as LandingResolution.Refused
        assertTrue(refused.reason.contains("outside the territory"))
    }

    @Test
    fun `traversal out of a granted path is refused`() {
        val refused = resolver().resolve(
            ArtifactLanding.Explicit(Path.of("/work/atropos/../../etc")), granted
        )
        assertTrue(refused is LandingResolution.Refused)
    }

    @Test
    fun `an empty grant permits no explicit landing at all`() {
        val refused = resolver().resolve(
            ArtifactLanding.Explicit(Path.of("/work/atropos/out")), emptyList()
        )
        assertTrue(refused is LandingResolution.Refused, "absence of a grant is not permission")
    }
}
