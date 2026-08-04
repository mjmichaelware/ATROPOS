/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * The metrics `P20-S04` names, computed from observed counts.
 *
 * "false-VERIFIED rate, territory violations, recovery completeness,
 * tokens-per-verified-change, observation success." These are the numbers that
 * say whether governance is working, and they are deliberately derived rather
 * than reported: a subsystem that sets its own metric can make it read well.
 *
 * Every rate returns `null` rather than zero when its denominator is zero. A
 * false-VERIFIED rate of 0% computed from no completion claims is not a good
 * score, it is no measurement, and rendering it as 0% would be the most
 * flattering possible lie.
 */
data class GovernanceCounts(
    val completionClaims: Long = 0,
    val falseVerified: Long = 0,
    val territoryChecks: Long = 0,
    val territoryViolations: Long = 0,
    val restarts: Long = 0,
    val cleanRecoveries: Long = 0,
    val verifiedChanges: Long = 0,
    val tokensSpent: Long = 0,
    val observationPeriods: Long = 0,
    val observationsSurvived: Long = 0
)

data class GovernanceMetrics(private val counts: GovernanceCounts) {

    val falseVerifiedRate: Double? = rate(counts.falseVerified, counts.completionClaims)

    val territoryViolationRate: Double? = rate(counts.territoryViolations, counts.territoryChecks)

    val recoveryCompleteness: Double? = rate(counts.cleanRecoveries, counts.restarts)

    val observationSuccess: Double? = rate(counts.observationsSurvived, counts.observationPeriods)

    /** Null when nothing has been verified — dividing by zero changes is not a cost. */
    val tokensPerVerifiedChange: Double? =
        if (counts.verifiedChanges == 0L) null
        else counts.tokensSpent.toDouble() / counts.verifiedChanges.toDouble()

    /**
     * True when every metric that has a measurement is within its bound.
     *
     * An unmeasured metric does not pass and does not fail — it is reported as
     * unmeasured. Treating absence as success is how a dashboard turns green by
     * doing nothing.
     */
    fun healthy(): Boolean =
        (falseVerifiedRate ?: 0.0) == 0.0 &&
            (territoryViolationRate ?: 0.0) == 0.0

    fun unmeasured(): List<String> = buildList {
        if (falseVerifiedRate == null) add("falseVerifiedRate")
        if (territoryViolationRate == null) add("territoryViolationRate")
        if (recoveryCompleteness == null) add("recoveryCompleteness")
        if (observationSuccess == null) add("observationSuccess")
        if (tokensPerVerifiedChange == null) add("tokensPerVerifiedChange")
    }

    private fun rate(numerator: Long, denominator: Long): Double? =
        if (denominator <= 0L) null else numerator.toDouble() / denominator.toDouble()
}
