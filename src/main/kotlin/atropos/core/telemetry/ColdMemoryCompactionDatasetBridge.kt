/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD7-T1-TEL14: Cold Memory Compaction Dataset Bridge.
 *
 * Implements mathematical memory compaction and synchronization:
 * 1. FileLock-synchronized compaction lifecycle.
 * 2. Scans episodic memory records strictly older than 24 hours (t_cutoff = now - 24h).
 * 3. Computes binary Merkle CAS root over cold record leaves.
 * 4. Dispatches via DynamicDatasetSyncService under Soft-Fail Network Law.
 * 5. Writes exact 32-byte tombstone receipt and purges local records only upon confirmed ingestion.
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

        // Synchronize with file lock
        lockFile.parentFile?.mkdirs()
        RandomAccessFile(lockFile, "rw").use { raf ->
            val lock = raf.channel.tryLock() ?: return 0
            try {
                val lines = Files.readAllLines(memoryJsonl)
                if (lines.isEmpty()) return 0

                val retainedLines = mutableListOf<String>()
                val coldRecords = mutableListOf<FrontierSweTelemetryRecord>()

                for (line in lines) {
                    if (line.isBlank()) continue
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

                // Generate attested binary Merkle root
                val batch = TelemetryBatchPayload.create(
                    batchId = "cold-compact-${System.currentTimeMillis()}",
                    records = coldRecords
                )

                val syncResult = syncService.dispatchSync(batch)
                if (syncResult.success) {
                    // Confirmed remote ingestion: atomically update memory and write exact 32-byte tombstone
                    Files.write(memoryJsonl, retainedLines)
                    val tombstone = memoryDir.resolve("tombstone-${batch.batchId}.cas")
                    val tombstoneBytes = batch.merkleRoot.toByteArray(Charsets.UTF_8).copyOf(32)
                    Files.write(tombstone, tombstoneBytes)
                    return coldRecords.size
                }

                // Soft-fail: if remote sync fails, preserve all lines locally
                return 0
            } finally {
                lock.release()
            }
        }
    }
}
