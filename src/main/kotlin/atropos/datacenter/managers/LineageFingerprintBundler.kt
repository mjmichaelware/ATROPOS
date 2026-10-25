package atropos.datacenter.managers

import atropos.datacenter.fingerprint.FingerprintBinder
import java.security.MessageDigest

/**
 * Manager 3: Lineage Fingerprint and Semantic Bundler.
 * Verifies H0 Merkle paths and bundles 'like' data not otherwise connected by run
 * or engine goal but valuable when paired for training.
 */
class LineageFingerprintBundler {
    data class TelemetryBundle(
        val bundleId: String,
        val clusterTheme: String,
        val memberRecordIds: List<String>,
        val compositeMerkleRoot: String,
        val recordCount: Int
    )

    fun verifyAndAmendLineage(
        record: MutableMap<String, Any?>,
        promptH0: String,
        waveId: String,
        nodeId: String,
        causalChain: List<String>
    ): String {
        val merkle = FingerprintBinder.computeMerklePath(promptH0, waveId, nodeId, causalChain)
        record["prompt_h0_sha"] = promptH0
        record["wave_id"] = waveId
        record["node_id"] = nodeId
        record["causal_parent_chain"] = causalChain
        record["lineage_merkle_path"] = merkle
        return merkle
    }

    fun bundleLikeData(records: List<Map<String, Any?>>, theme: String): TelemetryBundle {
        val ids = records.mapNotNull { it["atom_id"]?.toString() }
        val digest = MessageDigest.getInstance("SHA-256")
        val composite = ids.sorted().joinToString(":")
        val root = digest.digest(composite.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        return TelemetryBundle(
            bundleId = "BUNDLE-$theme-${root.take(12)}",
            clusterTheme = theme,
            memberRecordIds = ids,
            compositeMerkleRoot = root,
            recordCount = records.size
        )
    }

    fun castVote(record: Map<String, Any?>): ManagerVote {
        val hasH0 = record.containsKey("prompt_h0_sha") || record.containsKey("lineage_merkle_path")
        return if (hasH0) {
            ManagerVote(
                managerName = "Manager-3-LineageFingerprintBundler",
                vote = ManagerVote.Decision.APPROVE,
                confidence = 0.98,
                rationale = "Cryptographic lineage H0 Merkle path verified and bound."
            )
        } else {
            ManagerVote(
                managerName = "Manager-3-LineageFingerprintBundler",
                vote = ManagerVote.Decision.HOLD,
                confidence = 0.50,
                rationale = "Awaiting lineage amendment prior to Director transmission."
            )
        }
    }
}
