/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.time.Instant

data class FactoryTerminationBudget(
    val maxAtomAttempts: Int = 3,
    val maxRepairWaves: Int = 3,
    val maxProviderCallsPerAtom: Int = 4
) {
    init {
        require(maxAtomAttempts > 0 && maxRepairWaves > 0 && maxProviderCallsPerAtom > 0) {
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

/** Bounded schedule/finalization policy over the existing execution DAG. */
class FactoryObligationLoop(
    private val dagStore: DagStore,
    private val budget: FactoryTerminationBudget = FactoryTerminationBudget(),
    private val progressGuard: FactoryProgressGuard = FactoryProgressGuard(dagStore)
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

    /**
     * The generator and verifier are one bounded factory wave. Only after
     * their independent evidence passes may that wave terminalize its planned
     * atoms in the existing DagStore.
     */
    fun finalizeAfterVerifiedEvidence(dagId: String, freeze: FactoryAcceptanceFreeze): FactoryObligationSnapshot {
        require(freeze.sha256.matches(Regex("[0-9a-f]{64}"))) { "factory finalization requires an acceptance freeze hash" }
        val dag = dagStore.readDag(dagId) ?: return FactoryObligationSnapshot(
            openWork = 0,
            runnableAtomIds = emptyList(),
            blockedAtomIds = emptyList(),
            failedAtomIds = emptyList(),
            doneAtomIds = emptyList(),
            stopReason = "DAG missing during factory finalization"
        )
        val current = snapshot(dag)
        require(current.failedAtomIds.isEmpty() && current.blockedAtomIds.isEmpty()) {
            "factory finalization refused: failed or blocked atoms remain"
        }
        dag.nodes.filterNot { it.state.terminal }.forEach { node ->
            dagStore.writeNode(
                node.copy(
                    state = DagNodeState.COMPLETE,
                    result = "factory verified against acceptance_freeze_sha256=${freeze.sha256}",
                    lastMessage = "verified factory wave",
                    finishedAt = Instant.now()
                )
            )
        }
        return snapshot(dagStore.readDag(dagId) ?: dag)
    }

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
