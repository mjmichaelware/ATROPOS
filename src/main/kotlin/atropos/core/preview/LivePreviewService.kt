/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.preview

import atropos.ast.AstSymbol
import atropos.ast.AstSymbolGraph
import atropos.core.agent.AgentPatchExtractor
import atropos.core.multimodal.BrowserEvidenceResult
import atropos.core.multimodal.BrowserEvidenceStatus
import atropos.core.multimodal.LivePreviewEvidenceService
import atropos.core.visual.VisualComparison
import atropos.core.visual.VisualComparisonResult
import java.nio.file.Path
import java.security.MessageDigest

data class UiComponentImpact(
    val file: String,
    val symbolName: String,
    val componentType: String,
    val severity: String
)

data class ReloadResult(
    val ok: Boolean,
    val message: String,
    val snapshotId: String? = null,
    val touchedPaths: List<String> = emptyList()
)

class LivePreviewService(
    private val repoRoot: Path,
    private val evidenceService: LivePreviewEvidenceService = LivePreviewEvidenceService(),
    private val symbolGraph: AstSymbolGraph = AstSymbolGraph(repoRoot),
    private val patchExtractor: AgentPatchExtractor = AgentPatchExtractor()
) {
    private var width: Int = 1280
    private var height: Int = 720
    private var activePatchHash: String? = null

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
        return evidenceService.captureUrl(url)
    }

    fun captureStaticHtml(label: String, html: String, expectedText: String? = null): BrowserEvidenceResult {
        return evidenceService.captureStaticHtml(label, html, expectedText)
    }

    fun compareCaptures(
        baseline: BrowserEvidenceResult?,
        current: BrowserEvidenceResult
    ): VisualComparisonResult {
        val baselineEvidence = baseline?.let { toPreviewEvidence("preview-baseline", it) }
        val currentEvidence = toPreviewEvidence("preview-current", current)
        PreviewComparison.compare(baselineEvidence, currentEvidence)
        return VisualComparison.compareSnapshots(
            baseline = baselineEvidence?.let { baseline?.snapshot },
            current = currentEvidence.let { current.snapshot }
        )
    }

    fun toPreviewEvidence(requirementId: String, result: BrowserEvidenceResult): PreviewEvidence {
        val outcome = when {
            result.status == BrowserEvidenceStatus.CAPTURED && result.snapshot != null -> PreviewOutcome.RENDERED
            result.status == BrowserEvidenceStatus.UNSUPPORTED -> PreviewOutcome.NOT_RUN
            result.status == BrowserEvidenceStatus.FAILED -> PreviewOutcome.ERROR
            else -> PreviewOutcome.BLANK
        }
        return PreviewEvidence(
            id = result.snapshot?.id ?: "preview-${requirementId.hashCode()}",
            requirementId = requirementId,
            outcome = outcome,
            capturedAt = result.snapshot?.capturedAt ?: java.time.Instant.now(),
            screenshotSha256 = result.snapshot?.contentHash,
            accessibilityViolations = result.inspection?.findings.orEmpty(),
            detail = result.message
        )
    }

    fun hotReload(patch: String): ReloadResult {
        if (patch.isBlank()) {
            return ReloadResult(false, "Failed to hot reload: patch is empty")
        }
        val extraction = patchExtractor.extract(patch)
            ?: return ReloadResult(false, "Failed to hot reload: patch is not a unified diff")
        patchExtractor.validate(extraction.diff)?.let { refusal ->
            return ReloadResult(false, "Failed to hot reload: $refusal", touchedPaths = extraction.touchedPaths)
        }
        if (!extraction.hasHunkBody) {
            return ReloadResult(false, "Failed to hot reload: patch has no hunk body", touchedPaths = extraction.touchedPaths)
        }
        val hash = sha256(extraction.diff)
        activePatchHash = hash
        val impacted = inspectUI(extraction.touchedPaths)
        return ReloadResult(
            ok = true,
            message = "Hot reload preview updated: ${impacted.size} impacted symbol(s)",
            snapshotId = "reload-${hash.take(16)}",
            touchedPaths = extraction.touchedPaths
        )
    }

    fun activePatchFingerprint(): String? = activePatchHash

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

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
