/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration
import java.time.Instant

/**
 * ATOM CC3: CLOUD WORKSPACE STORAGE RETENTION AND PRUNING
 * Path: src/main/kotlin/atropos/core/storage/CloudWorkspaceStorageRetentionSupervisor.kt
 *
 * Enforces retention deadlines on stopped or abandoned cloud workspaces.
 * Archives persistent workspace volumes to cold storage and purges unreferenced
 * environments after the retention window expires to enforce storage ceilings.
 */
data class CloudWorkspaceRetentionRule(
    val workspaceId: String,
    val maxRetentionDays: Long = 30L,
    val archiveAfterDays: Long = 7L,
    val coldStorageTier: String = "cold-archive"
)

data class CloudStorageWatermark(
    val totalAllocatedBytes: Long,
    val softLimitBytes: Long,
    val hardLimitBytes: Long
) {
    val isSoftExceeded: Boolean get() = totalAllocatedBytes > softLimitBytes
    val isHardExceeded: Boolean get() = totalAllocatedBytes > hardLimitBytes
}

enum class RetentionAction {
    KEEP_ACTIVE,
    ARCHIVE_TO_COLD_STORAGE,
    PRUNE_IMMEDIATELY
}

data class RetentionAuditResult(
    val workspaceId: String,
    val lastAccessed: Instant,
    val ageDays: Long,
    val recommendedAction: RetentionAction,
    val reason: String
)

class CloudWorkspaceStorageRetentionSupervisor(
    private val defaultRetentionDays: Long = 30L,
    private val clock: () -> Instant = { Instant.now() }
) {
    fun evaluateWorkspaceRetention(
        workspaceId: String,
        lastAccessed: Instant,
        customRule: CloudWorkspaceRetentionRule? = null,
        watermark: CloudStorageWatermark? = null
    ): RetentionAuditResult {
        val now = clock()
        val age = Duration.between(lastAccessed, now)
        val ageDays = age.toDays()
        val rule = customRule ?: CloudWorkspaceRetentionRule(workspaceId, defaultRetentionDays)

        // Hard watermark ceiling forces aggressive pruning
        if (watermark != null && watermark.isHardExceeded && ageDays >= rule.archiveAfterDays) {
            return RetentionAuditResult(
                workspaceId = workspaceId,
                lastAccessed = lastAccessed,
                ageDays = ageDays,
                recommendedAction = RetentionAction.PRUNE_IMMEDIATELY,
                reason = "Hard storage watermark exceeded (${watermark.totalAllocatedBytes} > ${watermark.hardLimitBytes})"
            )
        }

        val action = when {
            ageDays >= rule.maxRetentionDays -> RetentionAction.PRUNE_IMMEDIATELY
            ageDays >= rule.archiveAfterDays -> RetentionAction.ARCHIVE_TO_COLD_STORAGE
            else -> RetentionAction.KEEP_ACTIVE
        }

        val reason = when (action) {
            RetentionAction.PRUNE_IMMEDIATELY -> "Workspace exceeded retention window of ${rule.maxRetentionDays} days"
            RetentionAction.ARCHIVE_TO_COLD_STORAGE -> "Workspace inactive past archive threshold of ${rule.archiveAfterDays} days"
            RetentionAction.KEEP_ACTIVE -> "Workspace active within retention bounds"
        }

        return RetentionAuditResult(
            workspaceId = workspaceId,
            lastAccessed = lastAccessed,
            ageDays = ageDays,
            recommendedAction = action,
            reason = reason
        )
    }

    companion object {
        val defaultSupervisor = CloudWorkspaceStorageRetentionSupervisor()
    }
}
