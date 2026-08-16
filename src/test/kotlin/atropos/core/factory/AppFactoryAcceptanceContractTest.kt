package atropos.core.factory

import atropos.core.multimodal.BrowserEvidenceStatus
import atropos.core.preview.LivePreviewService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Acceptance contract for the general factory and preview owners. */
class AppFactoryAcceptanceContractTest {
    @Test
    fun general_factory_plan_and_preview_owners_cover_factory_edges() {
        val root = Files.createTempDirectory("atropos-factory-acceptance-")
        val plan = AppFactoryRouter(repoRoot = root).plan("build a notes CLI with tests")
        assertEquals("notes", plan.projectSpec.intent.name)
        assertTrue(plan.steps.any { it.kind == FactoryStepKind.CODE })

        val preview = LivePreviewService(root)
        val first = preview.captureStaticHtml("notes", "<main>notes</main>", "notes")
        val second = preview.captureStaticHtml("notes", "<main>notes v2</main>", "notes")
        assertEquals(BrowserEvidenceStatus.CAPTURED, first.status)
        assertTrue(preview.compareCaptures(first, second).comparable)
        assertTrue(preview.getDiagnostics("<main>notes</main>").isEmpty())
        assertTrue(
            preview.hotReload(
                "diff --git a/src/App.kt b/src/App.kt\n" +
                    "--- a/src/App.kt\n+++ b/src/App.kt\n" +
                    "@@ -1 +1 @@\n-old\n+new\n"
            ).ok
        )
    }
}
