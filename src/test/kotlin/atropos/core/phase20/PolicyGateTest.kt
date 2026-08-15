/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.MetricId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyGateTest {

    @Test
    fun `PolicyGate allows valid context`() {
        val gate = PolicyGate()
        val now = Instant.now()
        val verdict = gate.evaluate(PolicyGateContext(), now)
        assertTrue(verdict.allowed)
    }

    @Test
    fun `P20-H01 rate and P20-H02 depth limits are enforced`() {
        val gate = PolicyGate(SelfImprovementBounds(maxDepth = 2, maxProposalsPerPeriod = 3))
        val now = Instant.now()

        val rateViolated = gate.evaluate(PolicyGateContext(proposalsInPeriod = 3), now)
        assertFalse(rateViolated.allowed)
        assertTrue(rateViolated.violations.any { it.contains("P20-H01") })

        val depthViolated = gate.evaluate(PolicyGateContext(depth = 3), now)
        assertFalse(depthViolated.allowed)
        assertTrue(depthViolated.violations.any { it.contains("P20-H02") })
    }

    @Test
    fun `P20-H03 budget bounds are enforced`() {
        val gate = PolicyGate(SelfImprovementBounds(maxLines = 100, tokenBudget = 50000L))
        val now = Instant.now()

        val linesViolated = gate.evaluate(PolicyGateContext(linesChanged = 101), now)
        assertFalse(linesViolated.allowed)
        assertTrue(linesViolated.violations.any { it.contains("P20-H03") && it.contains("lines") })

        val tokensViolated = gate.evaluate(PolicyGateContext(tokensSpentInPeriod = 60000L), now)
        assertFalse(tokensViolated.allowed)
        assertTrue(tokensViolated.violations.any { it.contains("P20-H03") && it.contains("Token") })
    }

    @Test
    fun `P20-H04 cooldown limits are enforced`() {
        val gate = PolicyGate()
        val now = Instant.now()

        val underFailureCooldown = gate.evaluate(PolicyGateContext(lastFailureAt = now.minusSeconds(60)), now)
        assertFalse(underFailureCooldown.allowed)
        assertTrue(underFailureCooldown.violations.any { it.contains("P20-H04") })
    }

    @Test
    fun `P20-H05 observation and P20-H06 quarantine limits are enforced`() {
        val gate = PolicyGate(SelfImprovementBounds(maxRetries = 2))
        val now = Instant.now()

        val underObs = gate.evaluate(PolicyGateContext(subsystemUnderObservationUntil = now.plusSeconds(30)), now)
        assertFalse(underObs.allowed)
        assertTrue(underObs.violations.any { it.contains("P20-H05") })

        val quarantine = gate.evaluate(PolicyGateContext(consecutiveFailures = 3), now)
        assertFalse(quarantine.allowed)
        assertTrue(quarantine.violations.any { it.contains("P20-H06") })
    }

    @Test
    fun `P20-S01 security non-regression and P20-S02 first canonical amendment compile checks are enforced`() {
        val gate = PolicyGate()
        val now = Instant.now()

        val regressedMetricBefore = listOf(AtroposMetric(MetricId.SECRET_SAFETY, 0.0, 1))
        val regressedMetricAfter = listOf(AtroposMetric(MetricId.SECRET_SAFETY, 1.0, 1))
        val securityRegressed = gate.evaluate(
            PolicyGateContext(safetyMetricsBefore = regressedMetricBefore, safetyMetricsAfter = regressedMetricAfter),
            now
        )
        assertFalse(securityRegressed.allowed)
        assertTrue(securityRegressed.violations.any { it.contains("P20-S01") })

        val compileFailure = gate.evaluate(
            PolicyGateContext(isVerifiedClaim = true, compileExitCode = 1),
            now
        )
        assertFalse(compileFailure.allowed)
        assertTrue(compileFailure.violations.any { it.contains("P20-S02 / P20-L01") })
    }

    @Test
    fun `P20-S03 coordination efficiency non-regression is enforced`() {
        val gate = PolicyGate()
        val now = Instant.now()

        val efficiencyRegressed = gate.evaluate(
            PolicyGateContext(efficiencyBefore = 1.0, efficiencyAfter = 1.5),
            now
        )
        assertFalse(efficiencyRegressed.allowed)
        assertTrue(efficiencyRegressed.violations.any { it.contains("P20-S03") })
    }
}
