/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dopamine

import atropos.core.agent.AgentJobRecord
import atropos.core.agent.AgentJobStatus
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeState
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryRecord

/**
 * Generalized, automatic dopamine reward and penalty listener for ATROPOS execution lifecycles.
 *
 * Hooks into task and DAG node completions across all workflows (including SWE-bench):
 * - On success (exit 0, verification passes): Triggers a +1 Dopamine event, extracts the
 *   full trajectory (prompts, tool calls, file mutations), and persists it to long-term memory.
 * - On failure (exit > 0, verification fails): Triggers a -1 Dopamine event and records the
 *   failure trajectory in long-term memory so the engine learns what to avoid.
 */
class AutomaticDopamineExecutionListener(
    private val dopamineTracker: ProviderDopamineRewardTracker = ProviderDopamineRewardTracker.SHARED,
    private val memoryStore: LocalMemoryStore = LocalMemoryStore()
) {

    fun onTaskCompleted(
        record: AgentJobRecord,
        changedFiles: List<String>,
        smokeExitCode: Int? = null,
        smokePassed: Boolean? = null
    ): MemoryRecord {
        val isSuccess = record.status == AgentJobStatus.COMPLETED &&
            (smokeExitCode == null || smokeExitCode == 0) &&
            (smokePassed != false)

        val trajectory = buildString {
            appendLine("=== TASK TRAJECTORY ===")
            appendLine("Task: ${record.task}")
            appendLine("Status: ${record.status.name}")
            appendLine("Provider: ${record.provider}")
            appendLine("ExitCode: ${smokeExitCode ?: if (isSuccess) 0 else 1}")
            if (!record.plan.isNullOrBlank()) {
                appendLine("--- Plan ---")
                appendLine(record.plan)
            }
            appendLine("--- Tool Calls & Operations ---")
            appendLine("Patch ID: ${record.appliedPatchId ?: record.patchId ?: "none"}")
            appendLine("Verification ID: ${record.verificationId ?: "none"}")
            appendLine("Repair ID: ${record.repairId ?: "none"}")
            appendLine("Smoke Command: ${record.smokeCommand ?: "none"}")
            appendLine("Smoke Result: ${record.smokeResult ?: "none"}")
            appendLine("--- File Mutations ---")
            if (changedFiles.isEmpty()) {
                appendLine("none")
            } else {
                changedFiles.forEach { appendLine("- $it") }
            }
            if (!isSuccess && !record.failureReason.isNullOrBlank()) {
                appendLine("--- Failure Reason ---")
                appendLine(record.failureReason)
            }
        }.trimEnd()

        return if (isSuccess) {
            dopamineTracker.recordSuccess(
                provider = record.provider,
                action = "agent.task",
                rewardValue = 1.0,
                details = "Task ${record.id} succeeded (exit 0, verified); mutations=${changedFiles.size}"
            )
            memoryStore.rememberReward(
                subjectId = record.id,
                title = "Task Success Trajectory: ${record.id}",
                body = trajectory,
                tags = listOf("trajectory", "dopamine_plus_1", "task_success", record.provider)
            )
        } else {
            val failureReason = record.failureReason ?: "task failed with exit ${smokeExitCode ?: 1}"
            dopamineTracker.recordFailure(
                provider = record.provider,
                action = "agent.task",
                errorType = record.status.name,
                exactMistake = failureReason,
                penaltyValue = -1.0
            )
            memoryStore.rememberFailure(
                subjectType = "agent.task",
                subjectId = record.id,
                title = "Task Failure Trajectory: ${record.id}",
                body = trajectory,
                tags = listOf("trajectory", "dopamine_minus_1", "task_failure", record.provider)
            )
        }
    }

    fun onDagNodeCompleted(
        node: DagNode,
        result: DagNodeExecutionResult,
        changedFiles: List<String> = emptyList()
    ): MemoryRecord {
        val isSuccess = result.ok && result.state == DagNodeState.COMPLETE

        val trajectory = buildString {
            appendLine("=== DAG NODE TRAJECTORY ===")
            appendLine("Node ID: ${node.id}")
            appendLine("Label: ${node.label}")
            appendLine("Action: ${node.action.name}")
            appendLine("State: ${result.state.name}")
            appendLine("Ok: ${result.ok}")
            appendLine("ExitCode: ${if (isSuccess) 0 else 1}")
            appendLine("--- Tool Calls / Dependencies ---")
            appendLine("Dependencies: ${node.dependencies.joinToString(", ").ifBlank { "none" }}")
            appendLine("Territory: ${node.territory.joinToString(", ").ifBlank { "none" }}")
            appendLine("Action Payload: ${node.actionPayload ?: "none"}")
            appendLine("--- File Mutations ---")
            val files = if (changedFiles.isNotEmpty()) changedFiles else node.territory
            if (files.isEmpty()) {
                appendLine("none")
            } else {
                files.forEach { appendLine("- $it") }
            }
            if (!isSuccess && result.message.isNotBlank()) {
                appendLine("--- Failure Reason ---")
                appendLine(result.message)
            }
        }.trimEnd()

        val provider = node.claimOwner?.takeIf { it.isNotBlank() } ?: "system"

        return if (isSuccess) {
            dopamineTracker.recordSuccess(
                provider = provider,
                action = "dag.node",
                rewardValue = 1.0,
                details = "Node ${node.id} (${node.action}) completed successfully"
            )
            memoryStore.rememberReward(
                subjectId = node.id,
                title = "DAG Node Success Trajectory: ${node.id}",
                body = trajectory,
                tags = listOf("trajectory", "dopamine_plus_1", "dag_node_success", provider)
            )
        } else {
            dopamineTracker.recordFailure(
                provider = provider,
                action = "dag.node",
                errorType = result.state.name,
                exactMistake = result.message.ifBlank { "DAG node execution failed" },
                penaltyValue = -1.0
            )
            memoryStore.rememberFailure(
                subjectType = "dag.node",
                subjectId = node.id,
                title = "DAG Node Failure Trajectory: ${node.id}",
                body = trajectory,
                tags = listOf("trajectory", "dopamine_minus_1", "dag_node_failure", provider)
            )
        }
    }
}
