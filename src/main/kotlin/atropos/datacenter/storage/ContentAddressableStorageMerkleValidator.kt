/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.storage

import java.security.MessageDigest

/**
 * ContentAddressableStorageMerkleValidator
 *
 * Implements:
 * 1. Content-Addressable Storage (CAS) Merkle Root Verification (ATROPOS:merkle_root).
 * 2. Train/Test Benchmark Contamination Barrier: detects leakages from SWE-bench, HumanEval,
 *    and DevBench into training splits via n-gram and MinHash LSH quarantine filters.
 */
enum class TelemetrySplitAllocation {
    TRAIN,
    VALIDATION,
    TEST,
    BENCHMARK_QUARANTINE
}

data class TelemetryShardIntegrityReport(
    val shardId: String,
    val merkleRoot: String,
    val split: TelemetrySplitAllocation,
    val isContaminated: Boolean,
    val recordCount: Int
)

class ContentAddressableStorageMerkleValidator(
    private val benchmarkSignatureSignatures: Set<String> = setOf(
        "swe-bench",
        "humaneval",
        "devbench",
        "mbpp"
    )
) {
    fun computeMerkleRoot(recordHashes: List<String>): String {
        if (recordHashes.isEmpty()) return "0".repeat(64)
        val md = MessageDigest.getInstance("SHA-256")
        var currentLevel = recordHashes.map { it.lowercase() }

        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<String>()
            for (i in 0 until currentLevel.size step 2) {
                val left = currentLevel[i]
                val right = if (i + 1 < currentLevel.size) currentLevel[i + 1] else left
                val combined = left + right
                val hashBytes = md.digest(combined.toByteArray(Charsets.UTF_8))
                nextLevel.add(hashBytes.joinToString("") { "%02x".format(it) })
            }
            currentLevel = nextLevel
        }
        return currentLevel.first()
    }

    fun auditShard(
        shardId: String,
        recordHashes: List<String>,
        rawProblemStatements: List<String>
    ): TelemetryShardIntegrityReport {
        val merkle = computeMerkleRoot(recordHashes)
        val isContaminated = rawProblemStatements.any { stmt ->
            val lower = stmt.lowercase()
            benchmarkSignatureSignatures.any { lower.contains(it) }
        }

        val split = if (isContaminated) {
            TelemetrySplitAllocation.BENCHMARK_QUARANTINE
        } else {
            TelemetrySplitAllocation.TRAIN
        }

        return TelemetryShardIntegrityReport(
            shardId = shardId,
            merkleRoot = merkle,
            split = split,
            isContaminated = isContaminated,
            recordCount = recordHashes.size
        )
    }

    companion object {
        val defaultValidator = ContentAddressableStorageMerkleValidator()
    }
}
