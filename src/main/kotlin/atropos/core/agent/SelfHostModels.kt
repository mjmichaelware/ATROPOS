package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagStatus

data class SelfHostGoal(
    val record: GoalRunRecord,
    val dag: DagDefinition?
)

data class SelfHostResult(
    val ok: Boolean,
    val message: String,
    val goal: SelfHostGoal? = null
)

data class SelfHostStatus(
    val goalId: String,
    val status: GoalRunStatus,
    val terminalCondition: GoalTerminalCondition?,
    val phase: String?,
    val currentNodeId: String?,
    val dagStatus: DagStatus?,
    val message: String
)

data class SelfHostBenchmark(
    val totalGoals: Int,
    val completed: Int,
    val failed: Int,
    val cancelled: Int,
    val recoveryRequired: Int,
    val totalContinuations: Int,
    val avgContinuations: Double,
    val status: String
)
