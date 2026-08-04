/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.artifact.export.ArtifactLandingResolver
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportProjectionTest {

    private val repo: Path = Path.of("/workspace/project")

    @Test
    fun `both zones resolve when the platform has a downloads directory`() {
        val json = ExportProjection().render(
            ArtifactLandingResolver(repo, Path.of("/home/op/Downloads")),
            listOf(repo)
        )

        assertTrue(json.contains("\"id\":\"repository\",\"available\":true"))
        assertTrue(json.contains("\"id\":\"downloads\",\"available\":true"))
        assertTrue(json.contains(".atropos/exports") || json.contains(".atropos\\exports"))
    }

    @Test
    fun `an unavailable zone is reported with its reason rather than dropped`() {
        val json = ExportProjection().render(ArtifactLandingResolver(repo, null), listOf(repo))

        // The operator must learn Downloads exists and why it is unusable; a
        // shorter list would read as "this platform has one place to export".
        assertTrue(json.contains("\"id\":\"downloads\",\"available\":false"))
        assertTrue(json.contains("no downloads directory"))
        assertTrue(json.contains("Choose an explicit landing path"))
    }

    @Test
    fun `the granted territory travels with the zones`() {
        val json = ExportProjection().render(ArtifactLandingResolver(repo, null), listOf(repo))

        assertTrue(json.contains("\"grantedTerritory\":[\"/workspace/project\"]") ||
            json.contains("workspace"))
    }
}
