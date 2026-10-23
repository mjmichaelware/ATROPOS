/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater

/**
 * SD7-T1-TEL01: Batch Payload container for multi-record telemetry batches.
 * Supports Merkle attestation, binary packing, and dictionary compression.
 */
data class TelemetryBatchPayload(
    val batchId: String,
    val records: List<FrontierSweTelemetryRecord>,
    val merkleRoot: String = computeMerkleRoot(records),
    val totalRecords: Int = records.size,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    /**
     * Serializes records into canonical newline-delimited JSONL format.
     */
    fun toJsonLines(): String = buildString {
        records.forEach { record ->
            append(record.canonicalJson())
            append("\n")
        }
    }

    /**
     * Compresses the JSONL payload into a compact byte array (using deflate/zstd).
     */
    fun compress(): ByteArray {
        val raw = toJsonLines().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream(raw.size)
        val buffer = ByteArray(4096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    companion object {
        fun computeMerkleRoot(records: List<FrontierSweTelemetryRecord>): String {
            if (records.isEmpty()) return "0".repeat(64)
            var currentLayer = records.map { it.payloadHash }
            val md = MessageDigest.getInstance("SHA-256")

            while (currentLayer.size > 1) {
                val nextLayer = mutableListOf<String>()
                for (i in currentLayer.indices step 2) {
                    val left = currentLayer[i]
                    val right = if (i + 1 < currentLayer.size) currentLayer[i + 1] else left
                    val combined = md.digest((left + right).toByteArray(Charsets.UTF_8))
                    nextLayer.add(combined.joinToString("") { "%02x".format(it) })
                }
                currentLayer = nextLayer
            }
            return currentLayer.first()
        }
    }
}
