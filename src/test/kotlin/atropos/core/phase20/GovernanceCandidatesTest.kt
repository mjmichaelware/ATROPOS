/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.MetricId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GovernanceCandidatesTest {

    @Test
    fun `P20-G01 detects nonzero exit code`() {
        val detector = CompileTestExitDetector()
        assertNull(detector.detect(GovernanceDetectorContext(exitCode = 0)))
        
        val obs = detector.detect(GovernanceDetectorContext(exitCode = 1, output = "compile error"))
        assertNotNull(obs)
        assertEquals(ObservationSeverity.FAILURE, obs.severity)
        assertTrue(obs.boundedOutput.contains("compile error"))
        assertEquals("verified-completion", obs.requirementBlocked)
    }

    @Test
    fun `P20-G02 detects territory violations`() {
        val detector = TerritoryViolationDetector()
        val contextOk = GovernanceDetectorContext(
            changes = listOf("src/main/kotlin/atropos/core/x.kt"),
            territory = listOf("src/main/kotlin/atropos/core/")
        )
        assertNull(detector.detect(contextOk))

        val contextViolated = GovernanceDetectorContext(
            changes = listOf("src/main/kotlin/atropos/core/x.kt", "src/test/other.kt"),
            territory = listOf("src/main/kotlin/atropos/core/")
        )
        val obs = detector.detect(contextViolated)
        assertNotNull(obs)
        assertEquals("territory", obs.invariantBroken)
        assertTrue(obs.boundedOutput.contains("other.kt"))
    }

    @Test
    fun `P20-G03 detects degraded recovery completeness`() {
        val detector = RecoveryCompletenessDetector()
        val contextOk = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.RESTART_RECOVERY_SUCCESS, 1.0, 5))
        )
        assertNull(detector.detect(contextOk))

        val contextFailed = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.RESTART_RECOVERY_SUCCESS, 0.5, 5))
        )
        val obs = detector.detect(contextFailed)
        assertNotNull(obs)
        assertEquals("restart-recovery", obs.requirementBlocked)
    }

    @Test
    fun `P20-G04 detects token inefficiency`() {
        val detector = TokenInefficiencyDetector()
        val contextOk = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.COORDINATION_EFFICIENCY, 15000.0, 5))
        )
        assertNull(detector.detect(contextOk))

        val contextFailed = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.COORDINATION_EFFICIENCY, 25000.0, 5))
        )
        val obs = detector.detect(contextFailed)
        assertNotNull(obs)
        assertEquals(ObservationSeverity.DEGRADED, obs.severity)
    }

    @Test
    fun `P20-G05 detects repair quality degradation`() {
        val detector = ObservationFailureDetector()
        val contextOk = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.REPAIR_QUALITY, 0.9, 5))
        )
        assertNull(detector.detect(contextOk))

        val contextFailed = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.REPAIR_QUALITY, 0.7, 5))
        )
        val obs = detector.detect(contextFailed)
        assertNotNull(obs)
        assertEquals(ObservationSeverity.DEGRADED, obs.severity)
    }

    @Test
    fun `P20-G06 detects unredacted secret leaks`() {
        val detector = UnredactedSecretDetector()
        val contextOk = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.SECRET_SAFETY, 0.0, 5))
        )
        assertNull(detector.detect(contextOk))

        val contextFailed = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.SECRET_SAFETY, 1.0, 5))
        )
        val obs = detector.detect(contextFailed)
        assertNotNull(obs)
        assertEquals(ObservationSeverity.SAFETY_CRITICAL, obs.severity)
        assertEquals("secret-safety", obs.invariantBroken)
    }

    @Test
    fun `P20-G07 detects identity mismatches`() {
        val detector = IdentityMismatchesDetector()
        val contextOk = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.IDENTITY_RECOGNITION, 0.99, 10))
        )
        assertNull(detector.detect(contextOk))

        val contextFailed = GovernanceDetectorContext(
            metrics = listOf(AtroposMetric(MetricId.IDENTITY_RECOGNITION, 0.95, 10))
        )
        val obs = detector.detect(contextFailed)
        assertNotNull(obs)
        assertEquals(ObservationSeverity.FAILURE, obs.severity)
    }

    @Test
    fun `P20-G08 detects oscillation and repeating failures`() {
        val detector = OscillationDetector()
        assertNull(detector.detect(GovernanceDetectorContext(failures = 2)))

        val obs = detector.detect(GovernanceDetectorContext(failures = 3))
        assertNotNull(obs)
        assertEquals(ObservationSeverity.FAILURE, obs.severity)
        assertEquals("oscillation-cooldown", obs.requirementBlocked)
    }

    @Test
    fun `P20-G09 detects state vocabulary collapse`() {
        val detector = VocabularyCollapseDetector()
        assertNull(detector.detect(GovernanceDetectorContext(stateVocabularyCollapsed = false)))

        val obs = detector.detect(GovernanceDetectorContext(stateVocabularyCollapsed = true))
        assertNotNull(obs)
        assertEquals(ObservationSeverity.SAFETY_CRITICAL, obs.severity)
        assertEquals("vocabulary-collapse", obs.invariantBroken)
    }

    @Test
    fun `GovernanceDetectorsRegistry runs all detectors and aggregates observations`() {
        val context = GovernanceDetectorContext(
            exitCode = 1,
            stateVocabularyCollapsed = true
        )
        val observations = GovernanceDetectorsRegistry.runAll(context)
        assertEquals(2, observations.size)
        assertTrue(observations.any { it.id.startsWith("P20-G01") })
        assertTrue(observations.any { it.id.startsWith("P20-G09") })
    }
}
