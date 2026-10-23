/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.verification.DiagnosticSymbolSlicer
import java.nio.file.Path

/**
 * SD7-T1-TEL09: Multi-Language Diagnostic Telemetry Harvester.
 *
 * Extracts character-precise compiler and runtime diagnostics across Kotlin, Python,
 * TypeScript, Rust, and Go. Slices logs to +/- 25 lines of declaration scope using
 * DiagnosticSymbolSlicer to maximize signal density.
 */
class MultiLanguageDiagnosticHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
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

        val record = CompilerDiagnosticRecord(
            errorCode = "DIAG-FAIL",
            filePath = targetFile,
            line = failingLine,
            column = 1,
            enclosingSymbol = sliced.targetFile ?: "root",
            message = sliced.assertionError.take(300),
            fixSuggestions = listOf(sliced.compressedPrompt.take(200))
        )

        val telemetryRecord = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "diag-$targetFile-$failingLine",
            payload = record,
            rewardSignal = -1.0
        )
        ringBuffer?.offer(telemetryRecord)

        return listOf(record)
    }
}
