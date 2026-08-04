package atropos.core.agent

import atropos.core.recovery.RestartCoordinator
import atropos.core.security.RedactionFilter

class SelfHostStateSnapshotRecorder(
    private val restartCoordinator: RestartCoordinator,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val identityHasher: SelfHostSnapshotIdentityHasher = SelfHostSnapshotIdentityHasher()
) {
    fun captureEvidence(reason: String, goalId: String? = null): String =
        runCatching {
            val snapshot = restartCoordinator.snapshot()
            val goal = goalId?.let { id -> snapshot.goalRuns.firstOrNull { it.id == id } }
            val safeReason = redactionFilter.redact(reason)
            "state_snapshot reason=$safeReason id=${snapshot.id} hash=${identityHasher.hash(snapshot, goal)} goal=${goal?.id ?: "all"} node=${goal?.currentNodeId ?: "none"} territory=${goal?.territory?.joinToString(",") ?: "none"} goals=${snapshot.goalRuns.size} dags=${snapshot.dags.size} nodes=${snapshot.dagNodes.size} worktrees=${snapshot.worktrees.size}"
        }.getOrElse {
            "state_snapshot_failed reason=${redactionFilter.redact(reason)} error=${redactionFilter.redact(it.message ?: it.javaClass.simpleName)}"
        }
}
