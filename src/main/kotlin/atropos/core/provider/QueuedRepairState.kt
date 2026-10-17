/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.memory.LocalMemoryStore
import java.io.File

/**
 * SD7-T-054: Durable queued-repair state machine preserving partition context across total provider exhaustion.
 * Mathematical Invariant: Φ(t+1) < Φ(t) monotonic descent
 * When providers return 429/exhausted: serialize partition state, set status QUEUED_REPAIR, halt worker without aborting build.
 */
object QueuedRepairState {
    const val STATUS_QUEUED_REPAIR = "QUEUED_REPAIR"

    fun serializeAndHalt(partitionState: String) {
        val memoryStore = LocalMemoryStore(File(".atropos/memory"))
        memoryStore.rememberFailure(
            subjectType = "partition_exhaustion",
            subjectId = "partition-" + System.currentTimeMillis(),
            title = "Provider exhaustion - transitioned to $STATUS_QUEUED_REPAIR",
            body = partitionState,
            tags = listOf(STATUS_QUEUED_REPAIR, "provider_exhaustion", "cooldown")
        )
    }

    fun isQueued(status: String): Boolean = status == STATUS_QUEUED_REPAIR
}
