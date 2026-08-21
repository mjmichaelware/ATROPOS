package atropos.core.factory

import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore

data class FactoryProgressDecision(
    val allowed: Boolean,
    val reason: String,
    val evidenceSha256: String
)

/** Detects repeated failure signatures without creating a second retry system. */
class FactoryProgressGuard(
    private val dagStore: DagStore,
    private val identicalFailureLimit: Int = 3
) {
    private val failures = mutableMapOf<Pair<String, String>, Int>()
    private val fileHistory = mutableMapOf<String, ArrayDeque<String>>()

    init {
        require(identicalFailureLimit > 0) { "failure limit must be positive" }
    }

    fun observeFailure(dagId: String, atomId: String, rawFailure: String): FactoryProgressDecision {
        val signature = FactoryLineage.sha256(normalize(rawFailure))
        val key = atomId to signature
        val count = (failures[key] ?: 0) + 1
        failures[key] = count
        if (count < identicalFailureLimit) {
            return FactoryProgressDecision(true, "failure_signature_count=$count", signature)
        }

        val node = dagStore.readDag(dagId)?.findNode(atomId)
        if (node != null && !node.state.terminal) {
            dagStore.writeNode(
                node.copy(
                    state = DagNodeState.BLOCKED,
                    failureReason = "thrash: identical failure signature $signature repeated $count times",
                    lastMessage = "factory progress guard blocked repeated failure"
                )
            )
        }
        return FactoryProgressDecision(
            allowed = false,
            reason = "thrash detected for atom=$atomId repeated_failure_signature=$signature count=$count",
            evidenceSha256 = signature
        )
    }

    fun observeWrite(atomId: String, fileHashes: List<String>): FactoryProgressDecision {
        val fingerprint = FactoryLineage.sha256(fileHashes.sorted().joinToString("\n"))
        val history = fileHistory.getOrPut(atomId) { ArrayDeque() }
        history.addLast(fingerprint)
        while (history.size > 3) history.removeFirst()
        val oscillating = history.size == 3 && history.elementAt(0) == history.elementAt(2) && history.elementAt(0) != history.elementAt(1)
        return FactoryProgressDecision(
            allowed = !oscillating,
            reason = if (oscillating) "file hash oscillation detected for atom=$atomId" else "file fingerprint recorded",
            evidenceSha256 = fingerprint
        )
    }

    private fun normalize(rawFailure: String): String = rawFailure
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\d+"), "#")
        .trim()
}
