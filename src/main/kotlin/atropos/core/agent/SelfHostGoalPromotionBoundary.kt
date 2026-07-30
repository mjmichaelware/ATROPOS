package atropos.core.agent

import java.nio.file.Path

/** Records promotion boundary snapshots and delegates authority to the promotion service. */
class SelfHostGoalPromotionBoundary(
    private val store: GoalRunStore,
    private val stateSnapshotRecorder: SelfHostStateSnapshotRecorder,
    private val promotionService: SelfHostPromotionService
) {
    fun promote(goalId: String, candidateJar: Path, targetJar: Path, nodeId: String?): SelfHostPromotionResult {
        appendSnapshot(goalId, "pre-promote")
        val result = promotionService.promote(
            SelfHostPromotionRequest(
                goalId = goalId,
                nodeId = nodeId,
                candidateJar = candidateJar,
                targetJar = targetJar
            )
        )
        appendSnapshot(goalId, "post-promote")
        return result
    }

    private fun appendSnapshot(goalId: String, reason: String) {
        val record = store.resolve(goalId) ?: return
        store.update(record.copy(evidence = record.evidence + stateSnapshotRecorder.captureEvidence(reason, record.id)))
    }
}
