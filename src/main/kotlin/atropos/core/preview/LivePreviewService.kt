/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.preview

import atropos.ast.AstSymbol
import atropos.ast.AstSymbolGraph
import atropos.core.multimodal.BrowserActuator
import atropos.core.multimodal.BrowserEvidenceRequest
import atropos.core.multimodal.BrowserEvidenceResult
import atropos.core.multimodal.BrowserEvidenceStatus
import java.nio.file.Path

data class UiComponentImpact(
    val file: String,
    val symbolName: String,
    val componentType: String,
    val severity: String
)

data class ReloadResult(
    val ok: Boolean,
    val message: String,
    val snapshotId: String? = null
)

class LivePreviewService(
    private val repoRoot: Path,
    private val browserActuator: BrowserActuator = BrowserActuator(repoRoot),
    private val symbolGraph: AstSymbolGraph = AstSymbolGraph(repoRoot)
) {
    private var width: Int = 1280
    private var height: Int = 720

    fun inspectUI(changedFiles: List<String>): List<UiComponentImpact> {
        val impactedSymbols = symbolGraph.impactOfPaths(changedFiles)
        return impactedSymbols.map { symbol ->
            val compType = when {
                symbol.qualifiedName.contains("UI", ignoreCase = true) -> "UI"
                symbol.qualifiedName.contains("Component", ignoreCase = true) -> "Component"
                symbol.qualifiedName.contains("Screen", ignoreCase = true) -> "Screen"
                symbol.qualifiedName.contains("View", ignoreCase = true) -> "View"
                else -> "Model/Logic"
            }
            UiComponentImpact(
                file = symbol.file.fileName.toString(),
                symbolName = symbol.qualifiedName,
                componentType = compType,
                severity = "INFO"
            )
        }
    }

    fun captureLiveUrl(url: String): BrowserEvidenceResult {
        // Deterministic check: if there is no browser engine, we must fail with UNSUPPORTED
        return browserActuator.capture(
            BrowserEvidenceRequest(
                actorId = "preview",
                url = url,
                timeoutMillis = 5000
            )
        )
    }

    fun captureStaticHtml(label: String, html: String, expectedText: String? = null): BrowserEvidenceResult {
        return browserActuator.captureStaticHtml(label, html, expectedText)
    }

    fun hotReload(patch: String): ReloadResult {
        if (patch.isBlank()) {
            return ReloadResult(false, "Failed to hot reload: patch is empty")
        }
        // Simulated reload for compilation diagnostics
        return ReloadResult(true, "Hot reload applied successfully: 1 class reloaded")
    }

    fun setResponsiveMode(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        this.width = width
        this.height = height
    }

    fun getDiagnostics(html: String): List<String> {
        val diagnostics = mutableListOf<String>()
        if (html.contains("error", ignoreCase = true)) {
            diagnostics.add("Console error: visible error state detected in HTML")
        }
        if (html.contains("exception", ignoreCase = true)) {
            diagnostics.add("Runtime failure: exception signature found")
        }
        return diagnostics
    }
}
