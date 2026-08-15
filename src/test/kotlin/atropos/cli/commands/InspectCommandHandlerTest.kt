/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.multimodal.InspectionService
import atropos.core.multimodal.SnapshotService
import atropos.core.multimodal.SnapshotStore
import atropos.core.dag.DagService
import atropos.core.evidence.EvidenceCollector
import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactStore
import atropos.core.preview.LivePreviewService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class InspectCommandHandlerTest {

    @Test
    fun `handles full inspection command`() {
        val tempDir = Files.createTempDirectory("inspect-command-test-")
        val snapshotStore = SnapshotStore(tempDir)
        val snapshotService = SnapshotService(store = snapshotStore, repoRoot = tempDir)
        val inspectionService = InspectionService(
            snapshotService = snapshotService,
            repoRoot = tempDir
        )
        val handler = InspectCommandHandler(
            inspectionService = inspectionService,
            repoRoot = tempDir
        )
        val result = handler.handle(listOf("full"))
        assertTrue(result.contains("Full inspection: Multimodal: 0/0 passed"))
    }

    @Test
    fun `handles evidence collection subcommand`() {
        val tempDir = Files.createTempDirectory("inspect-command-test-")
        val store = ArtifactStore(tempDir)
        val pipeline = ArtifactPipeline(store = store)
        val collector = EvidenceCollector(
            repoRoot = tempDir,
            artifactPipeline = pipeline
        )
        val handler = InspectCommandHandler(
            inspectionService = InspectionService(repoRoot = tempDir),
            repoRoot = tempDir,
            evidenceCollector = collector
        )
        val result = handler.handle(listOf("evidence", "subject-1", "run-1"))
        assertTrue(result.contains("Collected evidence: evidence records=0"))
    }

    @Test
    fun `handles preview subcommand`() {
        val tempDir = Files.createTempDirectory("inspect-command-test-")
        val handler = InspectCommandHandler(
            inspectionService = InspectionService(repoRoot = tempDir),
            repoRoot = tempDir
        )
        val result = handler.handle(listOf("preview", "src/main/kotlin/atropos/core/preview/LivePreviewService.kt"))
        assertTrue(result.contains("UI components impacted: 0 component(s)"))
    }
}
