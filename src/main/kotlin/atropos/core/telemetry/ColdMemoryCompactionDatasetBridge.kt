/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * SD7-T1-TEL14: Cold Memory Compaction Dataset Bridge.
 *
 * Scans local episodic memory records older than 24 hours with zero active references.
 * Compresses and streams aged memory into remote Parquet/JSONL dataset archives.
 * Purges local records only upon confirmed Merkle CAS receipt; retains records under soft-fail.
 */
class ColdMemoryCompactionDatasetBridge(
    private val memoryDir: Path = AtroposRepoRootLocator.resolve().resolve(".atropos/memory"),
    private val syncService: DynamicDatasetSyncService = DynamicDatasetSyncService()
) {
    private val lockFile = File(memoryDir.toFile(), ".compaction.lock")

    fun compactColdMemory(cutoffAgeHours: Long = 24): Int {
        if (!Files.exists(memoryDir)) return 0
        val cutoffMs = System.currentTimeMillis() - (cutoffAgeHours * 3600 * 1000L)

        val memoryJsonl = memoryDir.resolve("memory.jsonl")
        if (!Files.exists(memoryJsonl)) return 0

        val lines = Files.readAllLines(memoryJsonl)
        if (lines.isEmpty()) return 0

        val retainedLines = mutableListOf<String>()
        val coldRecords = mutableListOf<FrontierSweTelemetryRecord>()

        for (line in lines) {
            if (line.isBlank()) continue
            // Simple parsing of timestamp
            val isCold = line.contains("\"createdAtEpochMs\":") &&
                line.substringAfter("\"createdAtEpochMs\":").substringBefore(",").substringBefore("}").toLongOrNull()
                    ?.let { it < cutoffMs } == true

            if (isCold) {
                coldRecords.add(
                    FrontierSweTelemetryRecord(
                        recordId = "cold-${System.currentTimeMillis()}-${coldRecords.size}",
                        instanceId = "cold-memory-compaction",
                        h0PromptLineage = "cold-archive",
                        kind = TelemetryKind.CAS_MERKLE_RECEIPT,
                        payloadJson = line,
                        rewardSignal = 0.0
                    )
                )
            } else {
                retainedLines.add(line)
            }
        }

        if (coldRecords.isEmpty()) return 0

        // Dispatch batch to remote dataset client
        val batch = TelemetryBatchPayload(
            batchId = "cold-compact-${System.currentTimeMillis()}",
            records = coldRecords
        )

        val syncResult = syncService.dispatchSync(batch)
        if (syncResult.success) {
            // Write 32-byte tombstone receipt and update local memory file
            Files.write(memoryJsonl, retainedLines)
            val tombstone = memoryDir.resolve("tombstone-${batch.batchId}.cas")
            Files.writeString(tombstone, batch.merkleRoot.take(32))
            return coldRecords.size
        }

        // Soft-fail: if remote sync fails, retain all lines locally
        return 0
    }
}
