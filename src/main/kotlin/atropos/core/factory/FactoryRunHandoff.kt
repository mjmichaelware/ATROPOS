/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import java.nio.file.Path

/** A journal-aligned projection of durable DAG/checkpoint state for resume. */
object FactoryRunHandoff {
    fun write(
        repoRoot: Path,
        runId: String,
        dagId: String,
        snapshot: FactoryObligationSnapshot,
        freeze: FactoryAcceptanceFreeze,
        lastGoodCommit: String? = null
    ): Path {
        val path = repoRoot.resolve(".atropos/runs/$runId/factory-handoff.md").normalize()
        require(path.startsWith(repoRoot.toAbsolutePath().normalize())) { "factory handoff escaped repository" }
        val content = buildString {
            appendLine("schema=factory-handoff-v1")
            appendLine("run_id=$runId")
            appendLine("planning_dag=$dagId")
            appendLine("acceptance_freeze_sha256=${freeze.sha256}")
            appendLine("open_work=${snapshot.openWork}")
            appendLine("next_runnable_atoms=${snapshot.runnableAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("blocked_atoms=${snapshot.blockedAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("failed_atoms=${snapshot.failedAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("done_atoms=${snapshot.doneAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("last_good_commit=${lastGoodCommit ?: "none"}")
            appendLine("stop_reason=${snapshot.stopReason ?: "none"}")
        }
        writeAtomically(path, content)
        return path
    }
}
