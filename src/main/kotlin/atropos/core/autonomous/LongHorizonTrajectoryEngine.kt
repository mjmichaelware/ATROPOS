/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.autonomous

import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONObject
import atropos.core.integration.McpEvidenceAndAuthorityPolicy

data class TrajectoryNode(
    val step: Int,
    val touchedFile: String,
    val patchDiff: String,
    val lyapunovPotential: Long,
    val parentSha: String?
)

data class TrajectoryRollbackDecision(
    val shouldRollback: Boolean,
    val targetStep: Int?,
    val reason: String
)

class LongHorizonTrajectoryEngine(
    val repoRoot: Path,
    val ledgerPolicy: McpEvidenceAndAuthorityPolicy = McpEvidenceAndAuthorityPolicy(repoRoot)
) {
    private val trajectoriesDir = repoRoot.resolve(".atropos/trajectories")

    init {
        Files.createDirectories(trajectoriesDir)
    }

    fun appendTrajectoryStep(node: TrajectoryNode): Path {
        val nodeFile = trajectoriesDir.resolve("step_${node.step}.json")
        val json = JSONObject().apply {
            put("step", node.step)
            put("touchedFile", node.touchedFile)
            put("patchDiff", node.patchDiff)
            put("lyapunovPotential", node.lyapunovPotential)
            put("parentSha", node.parentSha ?: JSONObject.NULL)
        }
        Files.writeString(nodeFile, json.toString(2))
        ledgerPolicy.appendToLedger(
            toolName = "trajectory_step",
            callerId = "LongHorizonTrajectoryEngine",
            payload = json.toString()
        )
        return nodeFile
    }

    fun evaluateLyapunovConvergence(recentNodes: List<TrajectoryNode>): TrajectoryRollbackDecision {
        if (recentNodes.size < 3) {
            return TrajectoryRollbackDecision(false, null, "Insufficient trajectory history for Lyapunov evaluation")
        }
        val last3 = recentNodes.takeLast(3)
        val p0 = last3[0].lyapunovPotential
        val p1 = last3[1].lyapunovPotential
        val p2 = last3[2].lyapunovPotential

        // If potential strictly increased for 2 consecutive turns (divergence)
        if (p1 > p0 && p2 > p1) {
            val rollbackTarget = last3[0].step
            return TrajectoryRollbackDecision(
                shouldRollback = true,
                targetStep = rollbackTarget,
                reason = "Lyapunov potential strictly increased monotonically over 2 consecutive turns ($p0 -> $p1 -> $p2)"
            )
        }

        return TrajectoryRollbackDecision(
            shouldRollback = false,
            targetStep = null,
            reason = "Lyapunov convergence criteria satisfied: potential is monotonically stable"
        )
    }

    fun rollbackToStep(step: Int): Boolean {
        val targetNodeFile = trajectoriesDir.resolve("step_$step.json")
        return Files.exists(targetNodeFile)
    }
}
