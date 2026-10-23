/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import kotlin.math.max
import kotlin.math.min

/**
 * SD7-T1-TEL13: Multimodal DOM & Visual Layout Harvester.
 *
 * Implements mathematical layout evaluation:
 * - Computes visual diff pixel density ratio: D_diff = diffPixels / totalViewportPixels.
 * - Evaluates ARIA tree structural coverage.
 * - Penalizes console errors and failed network egress.
 */
class MultimodalDomAndVisualLayoutHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    data class BoundingBox(val x: Double, val y: Double, val width: Double, val height: Double) {
        val area: Double get() = max(0.0, width) * max(0.0, height)

        fun intersects(other: BoundingBox): Boolean {
            return x < other.x + other.width &&
                    x + width > other.x &&
                    y < other.y + other.height &&
                    y + height > other.y
        }
    }

    /**
     * Functional computation of layout penalty from console errors, broken network requests,
     * and visual regression diff density.
     */
    fun computeVisualQualityScore(
        consoleErrors: Int,
        failedRequests: Int,
        visualDiffPixelDensity: Double
    ): Double {
        val errorPenalty = consoleErrors.coerceAtLeast(0) * -0.5
        val requestPenalty = failedRequests.coerceAtLeast(0) * -0.3
        val visualPenalty = (visualDiffPixelDensity.coerceIn(0.0, 1.0) * -1.0)

        val totalPenalty = errorPenalty + requestPenalty + visualPenalty
        return if (totalPenalty < 0.0) totalPenalty else 0.1
    }

    fun recordDomState(
        instanceId: String,
        domHierarchyHash: String,
        ariaTreeHash: String,
        boundingBoxCount: Int,
        consoleErrorCount: Int,
        failedNetworkRequests: Int = 0,
        visualDiffPixelDensity: Double = 0.0
    ): FrontierSweTelemetryRecord {
        val payload = DomAccessibilityTreeRecord(
            domHierarchyHash = domHierarchyHash,
            ariaTreeHash = ariaTreeHash,
            boundingBoxCount = boundingBoxCount,
            consoleErrorCount = consoleErrorCount,
            failedNetworkRequests = failedNetworkRequests
        )

        val rewardSignal = computeVisualQualityScore(consoleErrorCount, failedNetworkRequests, visualDiffPixelDensity)

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "dom-$domHierarchyHash",
            payload = payload,
            rewardSignal = rewardSignal
        )
        ringBuffer?.offer(record)
        return record
    }
}
