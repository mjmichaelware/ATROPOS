/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GovernanceMetricsTest {

    @Test
    fun `an unmeasured rate is null, never a flattering zero`() {
        val metrics = GovernanceMetrics(GovernanceCounts())
        assertNull(metrics.falseVerifiedRate, "0% from no claims is no measurement")
        assertNull(metrics.recoveryCompleteness)
        assertNull(metrics.tokensPerVerifiedChange)
        assertEquals(5, metrics.unmeasured().size)
    }

    @Test
    fun `rates are computed from their own denominators`() {
        val metrics = GovernanceMetrics(
            GovernanceCounts(
                completionClaims = 100, falseVerified = 3,
                territoryChecks = 50, territoryViolations = 1,
                restarts = 4, cleanRecoveries = 3
            )
        )
        assertEquals(0.03, metrics.falseVerifiedRate)
        assertEquals(0.02, metrics.territoryViolationRate)
        assertEquals(0.75, metrics.recoveryCompleteness)
    }

    @Test
    fun `tokens per verified change refuses to divide by no changes`() {
        assertNull(GovernanceMetrics(GovernanceCounts(tokensSpent = 9_000)).tokensPerVerifiedChange)
        assertEquals(
            3_000.0,
            GovernanceMetrics(GovernanceCounts(tokensSpent = 9_000, verifiedChanges = 3)).tokensPerVerifiedChange
        )
    }

    @Test
    fun `any false VERIFIED makes the dashboard unhealthy`() {
        val metrics = GovernanceMetrics(GovernanceCounts(completionClaims = 100, falseVerified = 1))
        assertFalse(metrics.healthy(), "P20-G01 makes a single false VERIFIED the canonical deficiency")
    }

    @Test
    fun `a territory violation makes the dashboard unhealthy`() {
        assertFalse(
            GovernanceMetrics(GovernanceCounts(territoryChecks = 10, territoryViolations = 1)).healthy()
        )
    }

    @Test
    fun `clean observed counts are healthy`() {
        assertTrue(
            GovernanceMetrics(
                GovernanceCounts(completionClaims = 50, territoryChecks = 50)
            ).healthy()
        )
    }

    @Test
    fun `observation success is reported separately from health`() {
        val metrics = GovernanceMetrics(
            GovernanceCounts(observationPeriods = 4, observationsSurvived = 2)
        )
        assertEquals(0.5, metrics.observationSuccess)
    }
}
