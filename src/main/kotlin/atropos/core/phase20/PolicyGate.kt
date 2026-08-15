/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import java.time.Duration
import java.time.Instant

data class PolicyGateContext(
    val depth: Int = 1,
    val proposalsInPeriod: Int = 0,
    val filesChanged: Int = 1,
    val linesChanged: Int = 10,
    val retries: Int = 0,
    val tokensSpentInPeriod: Long = 0,
    val lastFailureAt: Instant? = null,
    val lastPromotionAt: Instant? = null,
    val subsystemUnderObservationUntil: Instant? = null,
    val consecutiveFailures: Int = 0,
    val compileExitCode: Int? = 0,
    val isVerifiedClaim: Boolean = false,
    val safetyMetricsBefore: List<AtroposMetric> = emptyList(),
    val safetyMetricsAfter: List<AtroposMetric> = emptyList(),
    val efficiencyBefore: Double = 1.0,
    val efficiencyAfter: Double = 1.0
)

data class PolicyGateVerdict(
    val allowed: Boolean,
    val reason: String,
    val violations: List<String>
)

class PolicyGate(
    private val bounds: SelfImprovementBounds = SelfImprovementBounds()
) {
    fun evaluate(context: PolicyGateContext, now: Instant): PolicyGateVerdict {
        val violations = mutableListOf<String>()

        // P20-H01: Rate Limit
        if (context.proposalsInPeriod >= bounds.maxProposalsPerPeriod) {
            violations.add("P20-H01: Rate limit exceeded (${context.proposalsInPeriod}/${bounds.maxProposalsPerPeriod} proposals)")
        }

        // P20-H02: Depth Limit
        if (context.depth > bounds.maxDepth) {
            violations.add("P20-H02: Chain depth ${context.depth} exceeds limit ${bounds.maxDepth}")
        }

        // P20-H03: Budget limits (LOC & Tokens)
        if (context.linesChanged > bounds.maxLines) {
            violations.add("P20-H03: Proposal size ${context.linesChanged} lines exceeds budget ${bounds.maxLines}")
        }
        if (context.tokensSpentInPeriod > bounds.tokenBudget) {
            violations.add("P20-H03: Token spend ${context.tokensSpentInPeriod} exceeds budget ${bounds.tokenBudget}")
        }

        // P20-H04: Cooldown period
        val cooldownDuration = Duration.ofMinutes(30)
        context.lastFailureAt?.let { failTime ->
            if (Duration.between(failTime, now) < cooldownDuration) {
                violations.add("P20-H04: Under failure cooldown until ${failTime.plus(cooldownDuration)}")
            }
        }
        context.lastPromotionAt?.let { promoTime ->
            if (Duration.between(promoTime, now) < cooldownDuration) {
                violations.add("P20-H04: Under promotion cooldown until ${promoTime.plus(cooldownDuration)}")
            }
        }

        // P20-H05: Observation period
        context.subsystemUnderObservationUntil?.let { obsUntil ->
            if (obsUntil.isAfter(now)) {
                violations.add("P20-H05: Subsystem is under active observation until $obsUntil")
            }
        }

        // P20-H06: Quarantine
        if (context.consecutiveFailures >= bounds.maxRetries + 1) {
            violations.add("P20-H06: Subsystem is in quarantine due to ${context.consecutiveFailures} consecutive failures")
        }

        // P20-S01: Non-regression of security/redaction safety metrics
        val beforeScore = context.safetyMetricsBefore.sumOf { it.value }
        val afterScore = context.safetyMetricsAfter.sumOf { it.value }
        if (afterScore > beforeScore) {
            violations.add("P20-S01: Security metrics regressed from $beforeScore to $afterScore")
        }

        // P20-S02: Verified compile/test gate completion (First Canonical Amendment P20-L01)
        if (context.isVerifiedClaim && context.compileExitCode != null && context.compileExitCode != 0) {
            violations.add("P20-S02 / P20-L01: Non-zero exit code ${context.compileExitCode} forbids VERIFIED completion status")
        }

        // P20-S03: Token efficiency non-regression
        if (context.efficiencyAfter > context.efficiencyBefore) {
            violations.add("P20-S03: Coordination token efficiency regressed from ${context.efficiencyBefore} to ${context.efficiencyAfter}")
        }

        return PolicyGateVerdict(
            allowed = violations.isEmpty(),
            reason = if (violations.isEmpty()) "Passed all Phase 20 constraints" else "Blocked by Phase 20 constraints: ${violations.joinToString("; ")}",
            violations = violations
        )
    }
}
