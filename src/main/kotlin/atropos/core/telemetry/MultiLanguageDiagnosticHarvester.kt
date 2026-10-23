/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.verification.DiagnosticSymbolSlicer
import java.nio.file.Path
import kotlin.math.max

/**
 * SD7-T1-TEL09: Multi-Language Diagnostic Telemetry Harvester.
 *
 * Extracts character-precise compiler and runtime diagnostics across Kotlin, Python,
 * TypeScript, Rust, and Go. Slices logs mathematically to +/- 25 lines of declaration scope
 * using DiagnosticSymbolSlicer to maximize signal density.
 */
class MultiLanguageDiagnosticHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    companion object {
        const val SCOPE_SLICING_WINDOW_LINES = 25
    }

    data class DiagnosticCoordinate(
        val line: Int,
        val column: Int,
        val scopeStartLine: Int,
        val scopeEndLine: Int
    )

    /**
     * Functional calculation of declaration scope coordinates bounded to +/- 25 lines.
     */
    fun computeScopeCoordinates(failingLine: Int): DiagnosticCoordinate {
        val line = max(1, failingLine)
        val start = max(1, line - SCOPE_SLICING_WINDOW_LINES)
        val end = line + SCOPE_SLICING_WINDOW_LINES
        return DiagnosticCoordinate(
            line = line,
            column = 1,
            scopeStartLine = start,
            scopeEndLine = end
        )
    }

    /**
     * Ingests raw build/test stdout and stderr, producing structured telemetry records.
     */
    fun harvestDiagnostics(
        instanceId: String,
        worktree: Path,
        stdout: String,
        stderr: String
    ): List<CompilerDiagnosticRecord> {
        val combined = "$stdout\n$stderr"
        if (combined.isBlank()) return emptyList()

        val sliced = DiagnosticSymbolSlicer.slice(worktree, combined)
        val targetFile = sliced.targetFile ?: "unknown"
        val failingLine = sliced.failingLine ?: 1
        val coords = computeScopeCoordinates(failingLine)

        val record = CompilerDiagnosticRecord(
            errorCode = "DIAG-FAIL",
            filePath = targetFile,
            line = coords.line,
            column = coords.column,
            enclosingSymbol = sliced.targetFile ?: "root",
            message = sliced.assertionError.take(300),
            fixSuggestions = listOf(sliced.compressedPrompt.take(200))
        )

        val telemetryRecord = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "diag-$targetFile-${coords.line}",
            payload = record,
            rewardSignal = -1.0
        )
        ringBuffer?.offer(telemetryRecord)

        return listOf(record)
    }
}
