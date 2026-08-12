/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.visual

import atropos.core.multimodal.SnapshotReference

/**
 * Deterministic comparison of captured visual evidence.
 *
 * Browser engines may be unavailable on a given host, but comparison of
 * already-captured evidence must remain portable and must never turn a
 * missing capture into a match.
 */
enum class VisualComparisonStatus {
    NO_BASELINE,
    UNCHANGED,
    CHANGED,
    INCOMPARABLE
}

data class VisualComparisonResult(
    val status: VisualComparisonStatus,
    val baselineHash: String? = null,
    val currentHash: String? = null,
    val reason: String? = null
) {
    val comparable: Boolean
        get() = status == VisualComparisonStatus.UNCHANGED ||
            status == VisualComparisonStatus.CHANGED
}

object VisualComparison {
    fun compareSnapshots(
        baseline: SnapshotReference?,
        current: SnapshotReference?
    ): VisualComparisonResult = compareHashes(
        baselineHash = baseline?.contentHash,
        currentHash = current?.contentHash,
        missingBaselineReason = "no baseline snapshot",
        missingCurrentReason = "no current snapshot"
    )

    fun compareHashes(
        baselineHash: String?,
        currentHash: String?,
        missingBaselineReason: String = "no baseline capture",
        missingCurrentReason: String = "no current capture"
    ): VisualComparisonResult {
        if (baselineHash.isNullOrBlank()) {
            return VisualComparisonResult(
                status = VisualComparisonStatus.NO_BASELINE,
                currentHash = currentHash,
                reason = missingBaselineReason
            )
        }
        if (currentHash.isNullOrBlank()) {
            return VisualComparisonResult(
                status = VisualComparisonStatus.INCOMPARABLE,
                baselineHash = baselineHash,
                reason = missingCurrentReason
            )
        }
        return if (baselineHash == currentHash) {
            VisualComparisonResult(
                status = VisualComparisonStatus.UNCHANGED,
                baselineHash = baselineHash,
                currentHash = currentHash
            )
        } else {
            VisualComparisonResult(
                status = VisualComparisonStatus.CHANGED,
                baselineHash = baselineHash,
                currentHash = currentHash
            )
        }
    }
}
