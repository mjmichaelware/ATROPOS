/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.agent.GoalRunRecord
import atropos.core.dag.DagNode
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Builds [ContextEnvelope] instances from available runtime context.
 *
 * Every field is populated from the best available source.  Missing
 * context (no current goal, DAG, node, etc.) results in empty strings
 * rather than nulls.
 */
object ContextEnvelopeFactory {

    /**
     * Create a minimal envelope when only the repository is known.
     * Used for simple /agent ask and /agent patch calls without a
     * surrounding goal or DAG.
     */
    fun createSimple(
        providerId: String,
        modelId: String,
        task: String,
        repoRoot: Path,
        branch: String = currentBranch(repoRoot),
        baselineCommit: String = currentCommit(repoRoot)
    ): ContextEnvelope {
        val envelope = ContextEnvelope(
            repository = repoRoot.fileName.toString(),
            repositoryRoot = repoRoot.toString(),
            branch = branch,
            baselineCommit = baselineCommit,
            task = task,
            providerId = providerId,
            modelId = modelId
        )
        return envelope.copy(canonicalContextHash = computeHash(envelope))
    }

    /**
     * Create a full envelope when goal and DAG node context is available.
     * Used for DAG-driven self-hosting goal execution.
     */
    fun createForGoal(
        providerId: String,
        modelId: String,
        task: String,
        repoRoot: Path,
        goal: GoalRunRecord,
        dagNode: DagNode? = null,
        branch: String = currentBranch(repoRoot),
        baselineCommit: String = currentCommit(repoRoot)
    ): ContextEnvelope {
        val envelope = ContextEnvelope(
            repository = repoRoot.fileName.toString(),
            repositoryRoot = repoRoot.toString(),
            branch = branch,
            baselineCommit = baselineCommit,
            goalId = goal.id,
            runId = goal.runId ?: goal.id,
            dagId = goal.dagId ?: "",
            nodeId = dagNode?.id ?: goal.currentNodeId ?: "",
            task = task,
            phaseOrPass = goal.activePhase ?: "",
            hierarchyRole = "worker",
            authority = "bounded",
            permissions = listOf("read_source", "write_patch"),
            assignedTerritory = goal.territory.ifEmpty { dagNode?.territory ?: emptyList() },
            prohibitedActions = listOf("modify_build_outputs", "modify_secrets", "modify_credentials", "modify_dot_git", "commit", "push"),
            activePolicy = "self_host_v1",
            providerId = providerId,
            modelId = modelId
        )
        return envelope.copy(canonicalContextHash = computeHash(envelope))
    }

    /**
     * Compute a deterministic SHA-256 hash of the envelope content
     * (excluding the hash field itself).
     */
    fun computeHash(envelope: ContextEnvelope): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val content = buildString {
            append("systemIdentity="); appendLine(envelope.systemIdentity)
            append("systemType="); appendLine(envelope.systemType)
            append("repository="); appendLine(envelope.repository)
            append("repositoryRoot="); appendLine(envelope.repositoryRoot)
            append("branch="); appendLine(envelope.branch)
            append("baselineCommit="); appendLine(envelope.baselineCommit)
            append("goalId="); appendLine(envelope.goalId)
            append("runId="); appendLine(envelope.runId)
            append("dagId="); appendLine(envelope.dagId)
            append("nodeId="); appendLine(envelope.nodeId)
            append("task="); appendLine(envelope.task)
            append("phaseOrPass="); appendLine(envelope.phaseOrPass)
            append("hierarchyRole="); appendLine(envelope.hierarchyRole)
            append("authority="); appendLine(envelope.authority)
            append("permissions="); appendLine(envelope.permissions.joinToString(","))
            append("assignedTerritory="); appendLine(envelope.assignedTerritory.joinToString(","))
            append("prohibitedActions="); appendLine(envelope.prohibitedActions.joinToString(","))
            append("activePolicy="); appendLine(envelope.activePolicy)
            append("providerId="); appendLine(envelope.providerId)
            append("modelId="); appendLine(envelope.modelId)
            append("contextVersion="); appendLine(envelope.contextVersion)
        }
        digest.update(content.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun currentBranch(repoRoot: Path): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }
    }

    private fun currentCommit(repoRoot: Path): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (_: Exception) { "unknown" }
    }
}
