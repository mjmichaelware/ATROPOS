/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.NeuralPathwayRewardEngine
import atropos.core.storage.StorageSupervisor
import java.io.File
import java.nio.file.Path

/**
 * SD7-T1-TEL19: Telemetry Backpressure & Disk Budget Governor.
 *
 * Adaptive backpressure and local disk governor that throttles telemetry collection
 * when local storage approaches physical watermarks or remote bandwidth is constrained.
 * Prioritizes high-value negatives (compile failures, surviving mutants, UNSAT cores, fraud attempts)
 * under backpressure.
 */
class TelemetryBackpressureAndDiskBudgetGovernor(
    private val spoolDir: Path,
    private val storageSupervisor: StorageSupervisor = StorageSupervisor(),
    private val maxSpoolBytesMobile: Long = 100 * 1024 * 1024L, // 100 MB
    private val maxSpoolBytesDesktop: Long = 1024 * 1024 * 1024L // 1 GB
) {
    private val isAndroid: Boolean = System.getProperty("java.vendor")?.contains("Android", ignoreCase = true) == true ||
        File("/data/data/com.termux").exists()

    val maxSpoolBudget: Long get() = if (isAndroid) maxSpoolBytesMobile else maxSpoolBytesDesktop

    /**
     * Determines whether a record should be accepted under current backpressure conditions.
     */
    fun shouldAcceptRecord(record: FrontierSweTelemetryRecord): Boolean {
        val currentSpoolBytes = measureSpoolSize()

        // 1. Check physical disk watermark
        if (!storageSupervisor.canAdmitPreflight(10 * 1024 * 1024L)) {
            // Under severe disk pressure, only accept Merkle CAS receipts
            return record.kind == TelemetryKind.CAS_MERKLE_RECEIPT
        }

        // 2. Under spool budget pressure, prioritize high-value negatives
        if (currentSpoolBytes >= maxSpoolBudget) {
            val isNegative = record.rewardSignal < 0.0 ||
                record.kind == TelemetryKind.MUTATION_KILL_RATIO ||
                record.kind == TelemetryKind.COMPILER_DIAGNOSTIC ||
                record.kind == TelemetryKind.CDCL_CONFLICT_CLAUSE

            return isNegative
        }

        return true
    }

    private fun measureSpoolSize(): Long {
        val file = spoolDir.toFile()
        if (!file.exists()) return 0L
        return file.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
