/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.io.File
import java.time.Instant

/**
 * ATOM 23.3: Scraping Operational Run History and Error Ledger.
 *
 * Maintains an auditable history of recent scraping and telemetry passes,
 * recording timestamps, record counts, and failure diagnostics.
 * Halts scraping automatically when local disk space drops below safety thresholds.
 *
 * Research Directive: Lyapunov stability models for dynamic backpressure control
 * to prevent storage exhaustion during bursty telemetry generation.
 */
class DecoupledDiskBudgetAuditGovernor(
    private val spoolDir: File,
    private val safetyThresholdFreeBytes: Long = 250L * 1024L * 1024L // 250MB
) {
    data class ScrapingAuditEntry(
        val timestamp: Instant,
        val recordCount: Int,
        val spoolSizeBytes: Long,
        val freeSpaceBytes: Long,
        val haltedDueToSafety: Boolean,
        val failureDiagnostic: String?
    )

    private val auditLedger = mutableListOf<ScrapingAuditEntry>()

    fun recordPass(recordCount: Int, failureDiagnostic: String? = null): ScrapingAuditEntry {
        val freeBytes = spoolDir.usableSpace
        val spoolBytes = if (spoolDir.exists()) {
            spoolDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }

        val halt = freeBytes < safetyThresholdFreeBytes
        val entry = ScrapingAuditEntry(
            timestamp = Instant.now(),
            recordCount = recordCount,
            spoolSizeBytes = spoolBytes,
            freeSpaceBytes = freeBytes,
            haltedDueToSafety = halt,
            failureDiagnostic = if (halt) "Disk space below 250MB safety watermark: $freeBytes bytes free" else failureDiagnostic
        )
        auditLedger.add(entry)
        return entry
    }

    fun isHalted(): Boolean = spoolDir.usableSpace < safetyThresholdFreeBytes

    fun getAuditHistory(): List<ScrapingAuditEntry> = auditLedger.toList()
}
