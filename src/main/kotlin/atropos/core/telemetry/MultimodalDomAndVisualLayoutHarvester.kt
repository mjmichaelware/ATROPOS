/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

/**
 * SD7-T1-TEL13: Multimodal DOM & Visual Layout Harvester.
 *
 * Captures rendered DOM structures, accessibility ARIA trees, bounding box geometries,
 * console errors, and VT100 screen buffer hashes.
 */
class MultimodalDomAndVisualLayoutHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    fun recordDomState(
        instanceId: String,
        domHierarchyHash: String,
        ariaTreeHash: String,
        boundingBoxCount: Int,
        consoleErrorCount: Int,
        failedNetworkRequests: Int = 0
    ) {
        val payload = DomAccessibilityTreeRecord(
            domHierarchyHash = domHierarchyHash,
            ariaTreeHash = ariaTreeHash,
            boundingBoxCount = boundingBoxCount,
            consoleErrorCount = consoleErrorCount,
            failedNetworkRequests = failedNetworkRequests
        )

        val penaltyOrReward = if (consoleErrorCount > 0) -0.5 else 0.1
        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "dom-$domHierarchyHash",
            payload = payload,
            rewardSignal = penaltyOrReward
        )
        ringBuffer?.offer(record)
    }
}
