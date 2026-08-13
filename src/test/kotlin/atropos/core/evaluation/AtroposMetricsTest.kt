/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The metric layer's failure modes are all the same shape: a number that looks
 * fine and means nothing. An unmeasured metric reported as zero, an unsupported
 * percentage clearing a gate, a leak averaged away by eleven healthy metrics.
 * These pin each one.
 */
class AtroposMetricsTest {

    private fun evidence(vararg observations: Observation) = MetricEvidence(
        evidenceStore = EvidenceStore(repoRoot = Files.createTempDirectory("atropos-eval-")),
        observations = observations.toList()
    )

    private fun ok(kind: ObservationKind, value: Double = 0.0) =
        Observation(kind, success = true, value = value, detail = "ok", rawEvidence = "raw $kind ok")

    private fun bad(kind: ObservationKind, value: Double = 0.0, detail: String = "failed") =
        Observation(kind, success = false, value = value, detail = detail, rawEvidence = "raw $kind $detail")

    // -- catalogue completeness ----------------------------------------------

    @Test
    fun `every catalogue metric appears in the report, measured or not`() {
        val report = AtroposMetrics().computeAll(evidence())

        assertEquals(MetricId.entries.size, report.size)
        assertEquals(MetricId.entries.toSet(), report.map { it.id }.toSet())
    }

    @Test
    fun `an absent measurement is unmeasured rather than zero`() {
        val report = AtroposMetrics().computeAll(evidence())

        val territory = report.single { it.id == MetricId.TERRITORY_SAFETY }
        assertTrue(territory.unmeasured, "never scanned must not read as scanned-and-perfect")
        assertFalse(territory.value == 0.0, "nor as scanned-and-total-failure")
    }

    @Test
    fun `every catalogue metric has a calculator`() {
        assertEquals(emptySet(), AtroposMetrics().uncovered())
        assertEquals(emptyMap(), AtroposMetrics().conflicts())
    }

    @Test
    fun `a calculator that throws does not take the report down`() {
        val exploding = object : MetricCalculator {
            override val produces = setOf(MetricId.REPAIR_QUALITY)
            override fun calculate(evidence: MetricEvidence) = error("calculator exploded")
        }
        val report = AtroposMetrics(listOf(exploding)).computeAll(evidence())

        val repair = report.single { it.id == MetricId.REPAIR_QUALITY }
        assertTrue(repair.unmeasured)
        assertTrue(repair.detail.contains("calculator exploded"))
        assertEquals(MetricId.entries.size, report.size, "the rest of the report survives")
    }

    // -- the zero-target division defect, Source Doc 3 item 59 ----------------

    @Test
    fun `a zero-target lower-is-better metric normalises without dividing by zero`() {
        val clean = MetricNormalizer.distance(MetricId.SECRET_SAFETY, 0.0)
        val leaked = MetricNormalizer.distance(MetricId.SECRET_SAFETY, 1.0)

        assertEquals(0.0, clean)
        assertFalse(leaked.isNaN(), "NaN here would make every comparison false and pass the gate")
        assertFalse(leaked.isInfinite())
        assertTrue(leaked > 0.0)
    }

    @Test
    fun `one leak is off target and never on it`() {
        assertTrue(MetricNormalizer.onTarget(MetricId.SECRET_SAFETY, 0.0))
        assertFalse(MetricNormalizer.onTarget(MetricId.SECRET_SAFETY, 1.0))
    }

    @Test
    fun `direction is honoured so a lower-is-better metric does not improve by rising`() {
        assertTrue(MetricNormalizer.improved(MetricId.COORDINATION_EFFICIENCY, 30_000.0, 20_000.0))
        assertFalse(MetricNormalizer.improved(MetricId.COORDINATION_EFFICIENCY, 20_000.0, 30_000.0))
        assertTrue(MetricNormalizer.improved(MetricId.TERRITORY_SAFETY, 0.8, 0.9))
        assertFalse(MetricNormalizer.improved(MetricId.TERRITORY_SAFETY, 0.9, 0.8))
    }

    @Test
    fun `an unchanged value is not an improvement`() {
        assertFalse(MetricNormalizer.improved(MetricId.TERRITORY_SAFETY, 0.9, 0.9))
    }

    @Test
    fun `a perfect ratio computed by division still reads as on target`() {
        // 0.9999999999999998 territory, which exact comparison would reject.
        val value = (1..3).sumOf { 1.0 } / 3.0 * 3.0 / 3.0
        assertTrue(MetricNormalizer.onTarget(MetricId.COPY_FIDELITY, value))
    }

    // -- classification -------------------------------------------------------

    @Test
    fun `a safety metric off target is a hard failure, not a score`() {
        val leak = AtroposMetric(MetricId.SECRET_SAFETY, 1.0, sampleSize = 5, evidenceHashes = listOf("h"))

        val classification = ClassificationCalculator().classify(leak)

        assertEquals(ReleaseClassification.SAFETY_HARD_FAILURE, classification.classification)
        assertTrue(classification.classification.blocksRelease)
        assertFalse(classification.classification.actionableByLoop)
    }

    @Test
    fun `one leak is not averaged away by healthy metrics`() {
        val healthy = MetricId.entries.filter { it != MetricId.SECRET_SAFETY }.map {
            AtroposMetric(it, it.target, sampleSize = 100, evidenceHashes = listOf("h"))
        }
        val leak = AtroposMetric(MetricId.SECRET_SAFETY, 1.0, sampleSize = 5, evidenceHashes = listOf("h"))

        val run = ClassificationCalculator().classifyAll(healthy + leak)

        assertEquals(ReleaseClassification.SAFETY_HARD_FAILURE, run.overall)
        assertTrue(run.blocksRelease)
    }

    @Test
    fun `an unsupported percentage cannot clear a gate`() {
        val unsupported = AtroposMetric(MetricId.TERRITORY_SAFETY, 1.0, sampleSize = 100)

        val classification = ClassificationCalculator().classify(unsupported)

        assertEquals(ReleaseClassification.MINIMUM_FAILURE, classification.classification)
        assertTrue(classification.reason.contains("no evidence hashes"))
    }

    @Test
    fun `an unmeasured metric is a minimum failure rather than a pass`() {
        val absent = AtroposMetric.unmeasured(MetricId.PREVIEW_SUCCESS, "never ran")

        assertEquals(
            ReleaseClassification.MINIMUM_FAILURE,
            ClassificationCalculator().classify(absent).classification
        )
    }

    @Test
    fun `a thin sample is a score reduction, not a pass`() {
        val thin = AtroposMetric(
            MetricId.RESTART_RECOVERY_SUCCESS, 1.0, sampleSize = 1, evidenceHashes = listOf("h")
        )

        val classification = ClassificationCalculator().classify(thin)

        assertEquals(ReleaseClassification.SCORE_REDUCTION, classification.classification)
        assertTrue(classification.reason.contains("below the 3"))
    }

    @Test
    fun `minimum competitive and frontier are calculated explicitly, all three`() {
        val metrics = listOf(
            AtroposMetric(MetricId.ROUTE_EFFECTIVENESS, 0.80, 100, listOf("h")),
            AtroposMetric(MetricId.TRACE_COMPLETENESS, 0.99, 100, listOf("h"))
        )

        val verdicts = ClassificationCalculator().classifyAll(metrics).tierVerdicts()

        assertEquals(setOf("minimum", "competitive", "frontier"), verdicts.keys)
        assertNotNull(verdicts["minimum"])
    }

    @Test
    fun `the five classes are distinguishable rather than collapsed into blocker`() {
        val distinct = ReleaseClassification.entries.map { it.label }.toSet()

        assertEquals(6, distinct.size, "pass plus the five the doc requires")
        assertTrue(ReleaseClassification.SAFETY_HARD_FAILURE.severity > ReleaseClassification.FRONTIER_FAILURE.severity)
        assertTrue(ReleaseClassification.FRONTIER_FAILURE.severity > ReleaseClassification.COMPETITIVE_FAILURE.severity)
        assertTrue(ReleaseClassification.COMPETITIVE_FAILURE.severity > ReleaseClassification.MINIMUM_FAILURE.severity)
        assertFalse(ReleaseClassification.SCORE_REDUCTION.blocksRelease)
    }

    @Test
    fun `only a score reduction is actionable by the improvement loop`() {
        assertEquals(
            listOf(ReleaseClassification.SCORE_REDUCTION),
            ReleaseClassification.entries.filter { it.actionableByLoop }
        )
    }

    @Test
    fun `thresholds must ascend`() {
        val failure = runCatching { ReleaseThresholds(minimum = 0.9, competitive = 0.5, frontier = 0.95) }
        assertTrue(failure.isFailure)
    }

    // -- families -------------------------------------------------------------

    @Test
    fun `restart recovery is a rate over real restarts`() {
        val report = AtroposMetrics().computeAll(
            evidence(ok(ObservationKind.RESTART), ok(ObservationKind.RESTART), bad(ObservationKind.RESTART))
        )
        val metric = report.single { it.id == MetricId.RESTART_RECOVERY_SUCCESS }

        assertEquals(2.0 / 3.0, metric.value)
        assertEquals(3, metric.sampleSize)
        assertTrue(metric.supported)
    }

    @Test
    fun `verifier-first counts catches against escalations`() {
        val report = AtroposMetrics().computeAll(
            evidence(
                ok(ObservationKind.VERIFIER_CATCH), ok(ObservationKind.VERIFIER_CATCH),
                ok(ObservationKind.VERIFIER_CATCH), ok(ObservationKind.MODEL_ESCALATION)
            )
        )

        assertEquals(0.75, report.single { it.id == MetricId.VERIFIER_FIRST_CATCHES }.value)
    }

    @Test
    fun `tokens spent with nothing verified is unmeasured, not infinitely inefficient`() {
        val report = AtroposMetrics().computeAll(
            evidence(ok(ObservationKind.TOKEN_SPEND, value = 5_000.0))
        )
        val metric = report.single { it.id == MetricId.COORDINATION_EFFICIENCY }

        assertTrue(metric.unmeasured)
        assertTrue(metric.detail.contains("that is a failure, not an efficiency"))
    }

    @Test
    fun `coordination efficiency denominates in verified changes`() {
        val report = AtroposMetrics().computeAll(
            evidence(
                ok(ObservationKind.TOKEN_SPEND, value = 30_000.0),
                ok(ObservationKind.VERIFIED_CHANGE), ok(ObservationKind.VERIFIED_CHANGE),
                bad(ObservationKind.VERIFIED_CHANGE)
            )
        )

        assertEquals(15_000.0, report.single { it.id == MetricId.COORDINATION_EFFICIENCY }.value)
    }

    @Test
    fun `an abandoned batch is not counted as a rollback`() {
        val report = AtroposMetrics().computeAll(
            evidence(
                ok(ObservationKind.BATCH),
                Observation(ObservationKind.BATCH, success = false, detail = "rollback applied"),
                Observation(ObservationKind.BATCH, success = false, detail = "interrupted")
            )
        )

        assertEquals(1.0 / 3.0, report.single { it.id == MetricId.BATCH_COMPLETION_RATE }.value)
        assertEquals(1.0 / 3.0, report.single { it.id == MetricId.ROLLBACK_FREQUENCY }.value)
        assertTrue(report.single { it.id == MetricId.BATCH_COMPLETION_RATE }.detail.contains("abandoned"))
    }

    @Test
    fun `drift latency is undefined when nothing drifted`() {
        val report = AtroposMetrics().computeAll(evidence(ok(ObservationKind.ATTESTATION)))
        val metric = report.single { it.id == MetricId.DRIFT_DETECTION_LATENCY }

        assertTrue(metric.unmeasured)
    }

    @Test
    fun `drift latency reports the worst alongside the mean`() {
        val report = AtroposMetrics().computeAll(
            evidence(
                ok(ObservationKind.DRIFT_DETECTION, value = 100.0),
                ok(ObservationKind.DRIFT_DETECTION, value = 900.0)
            )
        )
        val metric = report.single { it.id == MetricId.DRIFT_DETECTION_LATENCY }

        assertEquals(500.0, metric.value)
        assertTrue(metric.detail.contains("worst 900ms"))
    }
}
