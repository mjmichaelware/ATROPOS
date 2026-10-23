/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.security.MessageDigest

/**
 * Encapsulates a chunked batch of telemetry records.
 * Computes cryptographically attested binary Merkle roots over record leaves.
 */
data class TelemetryBatchPayload(
    val batchId: String,
    val records: List<FrontierSweTelemetryRecord>,
    val totalRecords: Int = records.size,
    val merkleRoot: String = computeBinaryMerkleRoot(records.map { it.payloadHash }),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonLines(): String = records.joinToString("\n") { it.canonicalJson() }

    companion object {
        fun create(batchId: String, records: List<FrontierSweTelemetryRecord>): TelemetryBatchPayload {
            val leafHashes = records.map { it.payloadHash }
            val root = computeBinaryMerkleRoot(leafHashes)
            return TelemetryBatchPayload(
                batchId = batchId,
                records = records,
                totalRecords = records.size,
                merkleRoot = root
            )
        }

        /**
         * Functioning binary Merkle tree computation bottom-up.
         */
        fun computeBinaryMerkleRoot(leafHashes: List<String>): String {
            if (leafHashes.isEmpty()) {
                return FrontierSweTelemetryRecord.sha256Hex("empty_batch_merkle_leaf")
            }
            var currentLevel = leafHashes
            while (currentLevel.size > 1) {
                val nextLevel = mutableListOf<String>()
                var i = 0
                while (i < currentLevel.size) {
                    val left = currentLevel[i]
                    val right = if (i + 1 < currentLevel.size) currentLevel[i + 1] else currentLevel[i]
                    nextLevel.add(FrontierSweTelemetryRecord.sha256Hex("$left:$right"))
                    i += 2
                }
                currentLevel = nextLevel
            }
            return currentLevel.first()
        }

        fun computeMerkleRoot(records: List<FrontierSweTelemetryRecord>): String {
            return computeBinaryMerkleRoot(records.map { it.payloadHash })
        }
    }
}
