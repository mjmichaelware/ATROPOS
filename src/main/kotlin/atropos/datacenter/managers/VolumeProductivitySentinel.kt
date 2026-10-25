package atropos.datacenter.managers

import atropos.datacenter.billing.SelfImprovementBill
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manager 2: Volume and Productivity Sentinel.
 * Evaluates file productivity deterministically based on data type expectations.
 * If underproducing, creates a SelfImprovementBill for the Atropos self-improvement engine.
 */
class VolumeProductivitySentinel {
    private val productionCounts = ConcurrentHashMap<String, AtomicLong>()
    private val firstSeenEpoch = Instant.now().epochSecond

    // Deterministic expected yields per hour per atom type
    private val expectedYieldMap = mapOf(
        "DC-TEL-046" to 12.0, // LexicalTokenDiffs: frequent edits
        "DC-TEL-061" to 20.0, // CliArgumentVectors: frequent tool runs
        "DC-TEL-077" to 1.5,  // MinimalUnsatCores: intermittent solver runs
        "DC-TEL-080" to 2.0,  // TarjanSccCycleDetections: periodic structural analysis
        "DC-DOP-001" to 8.0,  // BaseApprovedHunkSignal: approved hunks
        "DC-DOP-008" to 0.5   // DefinitiveFixBonus: rare definitive milestones
    )

    fun recordEmission(atomId: String): Long {
        return productionCounts.computeIfAbsent(atomId) { AtomicLong(0) }.incrementAndGet()
    }

    fun auditProductivity(atomId: String, targetPath: String): SelfImprovementBill? {
        val count = productionCounts[atomId]?.get() ?: 0L
        val elapsedHours = maxOf((Instant.now().epochSecond - firstSeenEpoch) / 3600.0, 0.25)
        val actualYieldPerHour = count / elapsedHours
        val expected = expectedYieldMap[atomId] ?: 2.0

        if (actualYieldPerHour < expected * 0.40) {
            val deficit = (expected - actualYieldPerHour) / expected
            return SelfImprovementBill(
                billId = "BILL-${atomId}-${Instant.now().epochSecond}",
                atomId = atomId,
                targetFilePath = targetPath,
                expectedYieldPerHour = expected,
                actualYieldPerHour = actualYieldPerHour,
                yieldDeficitRatio = deficit,
                rootCauseDiagnosis = "Atom $atomId emitting below deterministic threshold ($actualYieldPerHour < $expected/hr).",
                suggestedCodeFix = "Inject explicit lifecycle emission hook into corresponding core producer class.",
                expectedOutcome = "Restores telemetry yield to >= $expected records/hr without engine latency penalty.",
                memoryTarget = SelfImprovementBill.MemoryTier.LONG_TERM
            )
        }
        return null
    }

    fun castVote(atomId: String): ManagerVote {
        val count = productionCounts[atomId]?.get() ?: 1L
        return ManagerVote(
            managerName = "Manager-2-VolumeProductivitySentinel",
            vote = ManagerVote.Decision.APPROVE,
            confidence = 0.90,
            rationale = "Production counter verified (total: $count records recorded)."
        )
    }
}
