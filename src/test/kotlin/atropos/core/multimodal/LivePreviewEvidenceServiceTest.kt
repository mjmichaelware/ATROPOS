package atropos.core.multimodal

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivePreviewEvidenceServiceTest {
    @Test
    fun urlCaptureReturnsTypedUnsupportedOrPolicyBlockWithoutFakeSuccess() {
        val root = Files.createTempDirectory("atropos-browser-evidence-")
        val service = LivePreviewEvidenceService(
            BrowserActuator(
                repoRoot = root,
                snapshotService = SnapshotService(SnapshotStore(root), root)
            )
        )

        val result = service.captureUrl("https://example.com", expectedText = "Example")

        assertTrue(result.status == BrowserEvidenceStatus.POLICY_BLOCKED || result.status == BrowserEvidenceStatus.UNSUPPORTED)
        assertFalse(result.ok)
        assertTrue(result.message.contains("UNSUPPORTED") || result.message.contains("refused") || result.message.contains("not auto-allowed"))
    }

    @Test
    fun staticHtmlPreviewCapturesSnapshotAndInspectionEvidence() {
        val root = Files.createTempDirectory("atropos-static-preview-")
        val service = LivePreviewEvidenceService(
            BrowserActuator(
                repoRoot = root,
                snapshotService = SnapshotService(SnapshotStore(root), root)
            )
        )

        val result = service.captureStaticHtml("factory-proof", "<main>ATROPOS factory proof</main>", "factory proof")
        val persisted = SnapshotStore(root).loadSnapshot(result.snapshot!!.id)

        assertEquals(BrowserEvidenceStatus.CAPTURED, result.status)
        assertTrue(result.ok)
        assertTrue(result.snapshot?.contentHash?.isNotBlank() == true)
        assertEquals("static-preview:factory-proof", persisted?.source)
        assertTrue(result.inspection?.passed == true)
    }
}
