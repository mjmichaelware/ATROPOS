package atropos.datacenter.fingerprint

import java.security.MessageDigest

/**
 * Cryptographic Merkle lineage binder.
 * Binds prompt H0, waveId, DAG nodeId, and causal parent chain into an unforgeable trajectory fingerprint.
 */
object FingerprintBinder {
    fun computeMerklePath(promptH0: String, waveId: String, nodeId: String, parents: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$promptH0:$waveId:$nodeId:${parents.joinToString(",")}"
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
