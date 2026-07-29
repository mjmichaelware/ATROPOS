package atropos.core.agent

import atropos.core.recovery.RestartCoordinator

class SelfHostStateSnapshotRecorder(
    private val restartCoordinator: RestartCoordinator
) {
    fun captureEvidence(reason: String): String =
        runCatching {
            val snapshot = restartCoordinator.snapshot()
            "state_snapshot reason=$reason id=${snapshot.id} goals=${snapshot.goalRuns.size} dags=${snapshot.dags.size} nodes=${snapshot.dagNodes.size} worktrees=${snapshot.worktrees.size}"
        }.getOrElse {
            "state_snapshot_failed reason=$reason error=${it.message ?: it.javaClass.simpleName}"
        }
}
