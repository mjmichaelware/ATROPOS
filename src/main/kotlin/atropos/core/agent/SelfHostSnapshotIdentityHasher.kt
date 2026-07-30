package atropos.core.agent

import atropos.core.recovery.StateSnapshot
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Builds the deterministic identity hash for a recorded self-host snapshot. */
class SelfHostSnapshotIdentityHasher {
    fun hash(snapshot: StateSnapshot, goal: atropos.core.recovery.GoalRunSnapshot?): String {
        val input = listOf(
            snapshot.id,
            snapshot.capturedAt.toString(),
            goal?.id ?: "all",
            goal?.status ?: "none",
            goal?.currentNodeId ?: "none",
            goal?.territory.orEmpty().joinToString(","),
            goal?.evidenceHashes.orEmpty().joinToString(",")
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
