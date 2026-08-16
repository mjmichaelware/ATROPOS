/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant
import atropos.core.agent.GoalRunRecord

data class GoalRun(
    val runId: String,
    val goalId: String,
    val startedAt: Instant,
    val worktreePath: String,
    val status: String
) {
    companion object {
        /** Compatibility projection; durable ownership remains GoalRunRecord. */
        fun from(record: GoalRunRecord): GoalRun = GoalRun(
            runId = record.runId ?: record.id,
            goalId = record.goalId ?: record.id,
            startedAt = record.createdAt,
            worktreePath = record.territory.firstOrNull().orEmpty(),
            status = record.status.name
        )
    }
}
