/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

/**
 * SD7-T1-TEL16: Wave Orchestrator Telemetry Wiring.
 *
 * Hooks into full lifecycle orchestration events (Wave-0 Contract Synthesis through
 * Wave-4 Convergence). Records contract hashes, AST structural deltas, and verification
 * receipts onto the telemetry ring buffer with non-blocking offers.
 */
class WaveOrchestratorTelemetryWiring(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    val astSyncEngine: TreeSitterAstMultiWaveTelemetrySync by lazy { TreeSitterAstMultiWaveTelemetrySync(ringBuffer) }
    enum class OrchestrationWave {
        WAVE_0_CONTRACTS,
        WAVE_1_TEST_SUITE,
        WAVE_2_IMPLEMENTATION,
        WAVE_3_CONVERGENCE,
        WAVE_4_SEALING
    }

    fun onWaveTransition(
        instanceId: String,
        wave: OrchestrationWave,
        contractHash: String,
        passed: Boolean,
        metricsJson: String = "{}"
    ) {
        val payload = CasMerkleReceiptRecord(
            h0PromptHash = contractHash,
            chunkMerkleRoot = FrontierSweTelemetryRecord.sha256Hex(metricsJson),
            leafCount = 1,
            casEvidencePointer = "cas://wave/${wave.name.lowercase()}/$instanceId"
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "wave-${wave.ordinal}",
            payload = payload,
            rewardSignal = if (passed) 0.5 else -0.5
        )

        ringBuffer?.offer(record)
    }
}
