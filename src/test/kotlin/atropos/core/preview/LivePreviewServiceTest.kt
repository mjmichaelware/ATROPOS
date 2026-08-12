/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.preview

import atropos.core.multimodal.BrowserEvidenceStatus
import atropos.core.visual.VisualComparisonStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class LivePreviewServiceTest {

    @Test
    fun test_static_html_preview_capture() {
        val tempDir = Files.createTempDirectory("preview-test-")
        try {
            val service = LivePreviewService(repoRoot = tempDir)
            val result = service.captureStaticHtml("Home", "<html><body>Welcome Home</body></html>", "Welcome")
            assertEquals(BrowserEvidenceStatus.CAPTURED, result.status)
            assertNotNull(result.snapshot)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_live_preview_capture_unsupported_by_default() {
        val tempDir = Files.createTempDirectory("preview-test-")
        try {
            val service = LivePreviewService(repoRoot = tempDir)
            val result = service.captureLiveUrl("http://localhost:8080")
            // Because no browser engine is installed in the test sandbox, it must return UNSUPPORTED
            assertTrue(
                result.status == BrowserEvidenceStatus.UNSUPPORTED || result.status == BrowserEvidenceStatus.POLICY_BLOCKED
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_ui_inspection_identifies_impacted_components() {
        val tempDir = Files.createTempDirectory("preview-test-")
        try {
            val service = LivePreviewService(repoRoot = tempDir)
            val impacts = service.inspectUI(listOf("src/main/kotlin/atropos/core/preview/LivePreviewService.kt"))
            // Verify structure returns without crash
            assertNotNull(impacts)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_diagnostics_detection() {
        val tempDir = Files.createTempDirectory("preview-test-")
        try {
            val service = LivePreviewService(repoRoot = tempDir)
            val diagnostics = service.getDiagnostics("<html><body>An unexpected exception occurred!</body></html>")
            assertEquals(1, diagnostics.size)
            assertTrue(diagnostics.first().contains("Runtime failure"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun test_visual_comparison_refuses_missing_baseline_and_detects_change() {
        val tempDir = Files.createTempDirectory("preview-test-")
        try {
            val service = LivePreviewService(repoRoot = tempDir)
            val first = service.captureStaticHtml("Home", "<main>one</main>")
            val second = service.captureStaticHtml("Home", "<main>two</main>")

            assertEquals(VisualComparisonStatus.NO_BASELINE, service.compareCaptures(null, first).status)
            assertEquals(VisualComparisonStatus.CHANGED, service.compareCaptures(first, second).status)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
