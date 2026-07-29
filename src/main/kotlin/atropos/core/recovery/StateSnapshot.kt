package atropos.core.recovery

import java.time.Instant

data class GoalRunSnapshot(
    val id: String,
    val status: String,
    val dagId: String?,
    val currentNodeId: String?,
    val continuationCount: Int,
    val recoveryRequired: Boolean,
    val evidenceCount: Int,
    val territory: List<String> = emptyList(),
    val evidenceHashes: List<String> = emptyList()
)

data class DagSnapshot(
    val id: String,
    val label: String,
    val ready: Int,
    val running: Int,
    val blocked: Int,
    val complete: Int,
    val failed: Int
)

data class DagNodeSnapshot(
    val dagId: String,
    val nodeId: String,
    val state: String,
    val action: String,
    val territory: List<String>,
    val expectedOutputs: List<String>,
    val resultHash: String?,
    val failureHash: String?,
    val claimOwner: String?,
    val attempts: Int,
    val maxAttempts: Int
)

data class WorktreeSnapshot(
    val id: String,
    val jobId: String,
    val path: String,
    val verified: Boolean,
    val rolledBack: Boolean,
    val mergedBack: Boolean,
    val territory: List<String>
)

data class StateSnapshot(
    val id: String,
    val capturedAt: Instant,
    val goalRuns: List<GoalRunSnapshot>,
    val dags: List<DagSnapshot>,
    val dagNodes: List<DagNodeSnapshot> = emptyList(),
    val worktrees: List<WorktreeSnapshot>,
    val memoryRecords: Int,
    val recoveryReport: RecoveryReport? = null
)

data class DagNodeRestoreResult(
    val nodeId: String,
    val restored: Boolean,
    val reason: String
)

data class RestartCoordinatorResult(
    val ok: Boolean,
    val snapshot: StateSnapshot,
    val restoredNodes: List<DagNodeRestoreResult>,
    val message: String
)
