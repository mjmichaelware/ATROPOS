package atropos.datacenter.billing

import java.time.Instant

/**
 * Formal repair bill issued to the ATROPOS self-improvement engine
 * when a telemetry atom file is determined to be underproducing.
 */
data class SelfImprovementBill(
    val billId: String,
    val atomId: String,
    val targetFilePath: String,
    val timestamp: Instant = Instant.now(),
    val expectedYieldPerHour: Double,
    val actualYieldPerHour: Double,
    val yieldDeficitRatio: Double,
    val rootCauseDiagnosis: String,
    val suggestedCodeFix: String,
    val expectedOutcome: String,
    val memoryTarget: MemoryTier = MemoryTier.LONG_TERM
) {
    enum class MemoryTier { SHORT_TERM, LONG_TERM }
}
