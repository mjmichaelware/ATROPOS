package atropos.core.agent

data class SelfHostGoalStateUpdater(
    private val store: GoalRunStore,
    private val dagService: atropos.core.dag.DagExecutionService,
    private val stateSnapshotRecorder: SelfHostStateSnapshotRecorder
) {
    fun updatePhase(goalId: String, phase: String): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = appendSnapshotEvidence(store.update(record.copy(activePhase = phase)), "phase")
        return SelfHostResult(true, "phase updated to $phase", SelfHostGoal(updated, null))
    }

    fun updateCurrentNode(goalId: String, nodeId: String): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = appendSnapshotEvidence(store.update(record.copy(currentNodeId = nodeId)), "current-node")
        return SelfHostResult(true, "current node set to $nodeId", SelfHostGoal(updated, null))
    }

    fun setDag(goalId: String, dagId: String): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val dag = dagService.readDag(dagId) ?: return SelfHostResult(false, "DAG not found: $dagId")
        val updated = appendSnapshotEvidence(store.update(record.copy(dagId = dagId, territory = dag.nodes.flatMap { it.territory }.distinct(), currentNodeId = dag.findReadyNodes().firstOrNull()?.id)), "dag")
        return SelfHostResult(true, "DAG set to $dagId", SelfHostGoal(updated, dag))
    }

    fun addEvidence(goalId: String, evidenceEntry: String): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = appendSnapshotEvidence(store.update(record.copy(evidence = record.evidence + evidenceEntry)), "evidence")
        return SelfHostResult(true, "evidence added", SelfHostGoal(updated, null))
    }

    fun setTerritory(goalId: String, territory: List<String>): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = appendSnapshotEvidence(store.update(record.copy(territory = territory)), "territory")
        return SelfHostResult(true, "territory set", SelfHostGoal(updated, null))
    }

    fun updateVerifiedCheckpoint(goalId: String, checkpoint: String): SelfHostResult {
        val record = store.resolve(goalId) ?: return SelfHostResult(false, "goal not found: $goalId")
        val updated = appendSnapshotEvidence(store.update(record.copy(lastVerifiedCheckpoint = checkpoint)), "checkpoint")
        return SelfHostResult(true, "checkpoint updated to $checkpoint", SelfHostGoal(updated, null))
    }

    private fun appendSnapshotEvidence(record: GoalRunRecord, reason: String): GoalRunRecord =
        store.update(record.copy(evidence = record.evidence + stateSnapshotRecorder.captureEvidence(reason, record.id)))
}
