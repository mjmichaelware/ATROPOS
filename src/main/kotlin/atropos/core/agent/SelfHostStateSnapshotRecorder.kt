package atropos.core.agent

import atropos.core.recovery.RestartCoordinator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class SelfHostStateSnapshotRecorder(
    private val restartCoordinator: RestartCoordinator,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun captureEvidence(reason: String, goalId: String? = null): String =
        runCatching {
            val snapshot = restartCoordinator.snapshot()
            val goal = goalId?.let { id -> snapshot.goalRuns.firstOrNull { it.id == id } }
            val hashInput = listOf(
                snapshot.id,
                snapshot.capturedAt.toString(),
                goal?.id ?: "all",
                goal?.status ?: "none",
                goal?.currentNodeId ?: "none",
                goal?.territory.orEmpty().joinToString(","),
                goal?.evidenceHashes.orEmpty().joinToString(",")
            ).joinToString("|")
            val safeReason = redactionFilter.redact(reason)
            "state_snapshot reason=$safeReason id=${snapshot.id} hash=${sha256(hashInput)} goal=${goal?.id ?: "all"} node=${goal?.currentNodeId ?: "none"} territory=${goal?.territory?.joinToString(",") ?: "none"} goals=${snapshot.goalRuns.size} dags=${snapshot.dags.size} nodes=${snapshot.dagNodes.size} worktrees=${snapshot.worktrees.size}"
        }.getOrElse {
            "state_snapshot_failed reason=${redactionFilter.redact(reason)} error=${redactionFilter.redact(it.message ?: it.javaClass.simpleName)}"
        }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
