/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant

data class GoalRun(
    val runId: String,
    val goalId: String,
    val startedAt: Instant,
    val worktreePath: String,
    val status: String
)
