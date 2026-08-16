/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class ReplicaHealth(val replicaId: String, val healthy: Boolean, val checkedObjects: Int, val detail: String)

class ReplicaHealthService {
    fun assess(replicaId: String, expected: Map<String, String>, actual: Map<String, String>): ReplicaHealth {
        val mismatches = expected.keys.filter { actual[it] != expected[it] }
        return ReplicaHealth(replicaId, mismatches.isEmpty(), expected.size, if (mismatches.isEmpty()) "all checksums match" else "mismatches=${mismatches.size}")
    }
}
