/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.orchestration.WaveCheckpointLattice
import java.nio.file.Path
import java.time.Instant

data class FactoryTerminationBudget(
    val maxAtomAttempts: Int = 3,
    val maxRepairWaves: Int = 3,
    val maxProviderCallsPerAtom: Int = 4,
    val maxExecutionWaves: Int = 64
) {
    init {
        require(maxAtomAttempts > 0 && maxRepairWaves > 0 && maxProviderCallsPerAtom > 0 && maxExecutionWaves > 0) {
            "factory termination budgets must be positive"
        }
    }
}

data class FactoryObligationSnapshot(
    val openWork: Int,
    val runnableAtomIds: List<String>,
    val blockedAtomIds: List<String>,
    val failedAtomIds: List<String>,
    val doneAtomIds: List<String>,
    val stopReason: String? = null,
    val recordedAt: Instant = Instant.now()
) {
    val canComplete: Boolean get() = openWork == 0 && blockedAtomIds.isEmpty() && failedAtomIds.isEmpty()
}

data class FactoryLoopResult(
    val snapshot: FactoryObligationSnapshot,
    val wavesExecuted: Int,
    val terminationReason: String
)

/** Bounded schedule/finalization policy over the existing execution DAG. */
class FactoryObligationLoop(
    private val dagStore: DagStore,
    private val budget: FactoryTerminationBudget = FactoryTerminationBudget(),
    private val progressGuard: FactoryProgressGuard = FactoryProgressGuard(dagStore),
    private val repoRoot: Path = runCatching { atropos.core.AtroposRepoRootLocator.resolve() }.getOrDefault(Path.of("."))
) {
    fun recordFailure(dagId: String, atomId: String, failure: String): FactoryObligationSnapshot {
        val decision = progressGuard.observeFailure(dagId, atomId, failure)
        val dag = dagStore.readDag(dagId)
        return if (dag == null) {
            FactoryObligationSnapshot(0, emptyList(), emptyList(), emptyList(), emptyList(), decision.reason)
        } else {
            snapshot(dag).copy(stopReason = decision.reason.takeIf { !decision.allowed })
        }
    }

    fun beforeMutation(
        dag: DagDefinition,
        repairWaves: Int = 0,
        providerCalls: Int = 0
    ): FactoryObligationSnapshot {
        val snapshot = snapshot(dag)
        val overBudget = dag.nodes.any { it.attempts > budget.maxAtomAttempts } ||
            repairWaves > budget.maxRepairWaves ||
            providerCalls > budget.maxProviderCallsPerAtom * dag.nodes.size
        if (overBudget) {
            return snapshot.copy(stopReason = "termination budget exhausted: atom attempts, repair waves, or provider calls")
        }
        require(snapshot.runnableAtomIds.isNotEmpty()) {
            snapshot.stopReason ?: "factory DAG has no runnable canary atom"
        }
        return snapshot
    }

    /** Run real generator/verifier waves until the existing DAG is settled. */
    fun executeUntilSettled(
        dagId: String,
        freeze: FactoryAcceptanceFreeze,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult = executeUntilSettled(dagId, freeze, null, executeWave)

    fun executeUntilSettled(
        dagId: String,
        freeze: FactoryAcceptanceFreeze,
        repairHandler: ((dagId: String, failedNode: DagNode, errorLog: String, attempt: Int) -> RepairAtomResult)?,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult {
        var waves = 0
        var repairWaves = 0
        while (true) {
            val dag = dagStore.readDag(dagId) ?: return FactoryLoopResult(
                FactoryObligationSnapshot(0, emptyList(), emptyList(), emptyList(), emptyList(), "DAG missing during resume"),
                waves,
                "dag_missing"
            )
            val current = snapshot(dag)
            if (current.openWork == 0) return FactoryLoopResult(current, waves, "open_work=0")
            if (current.blockedAtomIds.isNotEmpty() || current.failedAtomIds.isNotEmpty()) {
                val failedNodes = dag.nodes.filter { it.state == DagNodeState.FAILED }
                if (failedNodes.isNotEmpty() && repairHandler != null && repairWaves < budget.maxRepairWaves) {
                    val target = failedNodes.first()
                    val errorLog = target.result ?: target.failureReason ?: "verification failed"
                    repairWaves++
                    val repairResult = repairHandler(dagId, target, errorLog, repairWaves)
                    if (repairResult.success) {
                        dagStore.writeNode(target.copy(
                            state = DagNodeState.READY,
                            attempts = target.attempts + 1,
                            result = null,
                            failureReason = null,
                            updatedAt = Instant.now()
                        ))
                        waves++
                        continue
                    }
                }
                return FactoryLoopResult(current, waves, "blocked_or_failed_atoms")
            }
            if (waves >= budget.maxExecutionWaves) {
                return FactoryLoopResult(current.copy(stopReason = "termination budget exhausted: waves"), waves, "budget_exhausted")
            }
            val ready = dag.findReadyNodes()
            if (ready.isEmpty()) return FactoryLoopResult(current.copy(stopReason = "open atoms are not runnable"), waves, "no_runnable_atoms")
            val completed = executeWave(ready)
            if (completed.isEmpty()) {
                val refreshedDag = dagStore.readDag(dagId) ?: dag
                val failedInWave = refreshedDag.nodes.firstOrNull { it.id in ready.map { r -> r.id } && (it.state == DagNodeState.FAILED || it.id !in completed) }
                    ?: ready.first()
                val errorLog = failedInWave.result ?: failedInWave.failureReason ?: "verification failed without output"
                val subDag = WaveCheckpointLattice.isolateSubDag(dag, failedInWave.id)
                if (repairHandler != null && repairWaves < budget.maxRepairWaves) {
                    repairWaves++
                    val repairResult = repairHandler(dagId, failedInWave, errorLog, repairWaves)
                    if (repairResult.success) {
                        dagStore.writeNode(failedInWave.copy(
                            state = DagNodeState.READY,
                            attempts = failedInWave.attempts + 1,
                            result = null,
                            failureReason = null,
                            updatedAt = Instant.now()
                        ))
                        waves++
                        continue
                    }
                }
                require(completed.isNotEmpty() || WaveCheckpointLattice.hasProgress(repoRoot, dagId, refreshedDag.nodes)) {
                    "factory wave executed without completing an atom"
                }
            }
            require(completed.all { id -> ready.any { it.id == id } }) { "factory wave completed an atom outside its runnable wave" }
            completed.forEach { id ->
                val node = dag.findNode(id) ?: error("factory wave returned unknown atom=$id")
                dagStore.writeNode(node.copy(
                    state = DagNodeState.COMPLETE,
                    result = "verified against acceptance_freeze_sha256=${freeze.sha256}",
                    finishedAt = Instant.now()
                ))
            }
            WaveCheckpointLattice.persistCheckpoint(repoRoot, dagId, dag.nodes, freeze.sha256)
            waves++
        }
    }

    fun resume(
        handoff: FactoryRunHandoffState,
        freeze: FactoryAcceptanceFreeze,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult = resume(handoff, freeze, null, executeWave)

    fun resume(
        handoff: FactoryRunHandoffState,
        freeze: FactoryAcceptanceFreeze,
        repairHandler: ((dagId: String, failedNode: DagNode, errorLog: String, attempt: Int) -> RepairAtomResult)?,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult {
        require(handoff.acceptanceFreezeSha256 == freeze.sha256) { "resume acceptance freeze does not match handoff" }
        return executeUntilSettled(handoff.dagId, freeze, repairHandler, executeWave)
    }

    fun recordRepairEvidence(
        freeze: FactoryAcceptanceFreeze,
        evidence: FactoryAcceptanceFreeze.RepairEvidence
    ): String = freeze.requireRepairEvidence(evidence)

    fun snapshot(dag: DagDefinition): FactoryObligationSnapshot {
        val states = dag.nodes.associate { it.id to it.state }
        val runnable = dag.nodes.filter { it.isReady(states) }.map { it.id }.sorted()
        val blocked = dag.nodes.filter { it.state == DagNodeState.BLOCKED }.map { it.id }.sorted()
        val failed = dag.nodes.filter { it.state == DagNodeState.FAILED }.map { it.id }.sorted()
        val done = dag.nodes.filter { it.state == DagNodeState.COMPLETE || it.state == DagNodeState.NOT_APPLICABLE }.map { it.id }.sorted()
        return FactoryObligationSnapshot(
            openWork = dag.nodes.count { !it.state.terminal },
            runnableAtomIds = runnable,
            blockedAtomIds = blocked,
            failedAtomIds = failed,
            doneAtomIds = done
        )
    }
}
