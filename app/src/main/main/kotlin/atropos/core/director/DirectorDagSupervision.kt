package atropos.core.director

import atropos.core.dag.DagExecutionResult

data class DirectorDagSupervision(
    val dagId: String,
    val label: String,
    val allowed: Boolean,
    val execution: DagExecutionResult?,
    val driftCount: Int,
    val blockingObservations: List<String>,
    val message: String
) {
    companion object {
        fun refused(dagId: String, message: String): DirectorDagSupervision = DirectorDagSupervision(
            dagId = dagId,
            label = "unknown",
            allowed = false,
            execution = null,
            driftCount = 0,
            blockingObservations = emptyList(),
            message = message
        )
    }
}
