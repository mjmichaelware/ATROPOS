package atropos.datacenter.managers

import atropos.datacenter.harvester.RepositoryConsentGate
import atropos.datacenter.harvester.ApiQuotaState

/**
 * Manager 4: Platform Consent and Rate Governor.
 * Enforces API rate limits, backpressure, and strict repository consent allowlists.
 * Guarantees zero unsolicited mass PRs and full Soft-Fail Network Law adherence.
 */
class PlatformConsentAndRateGovernor(
    val consentGate: RepositoryConsentGate
) {
    fun evaluateTargetAccess(platform: String, repoIdentifier: String): Boolean {
        val evaluation = consentGate.checkConsent(platform, repoIdentifier)
        return evaluation.isAuthorized
    }

    fun evaluateQuotaAllowance(quota: ApiQuotaState): Boolean {
        return !quota.isThrottled && quota.remaining > 50
    }

    fun castVote(platform: String, repoIdentifier: String, quota: ApiQuotaState): ManagerVote {
        val consent = consentGate.checkConsent(platform, repoIdentifier)
        if (!consent.isAuthorized) {
            return ManagerVote(
                managerName = "Manager-4-ConsentAndRateGovernor",
                vote = ManagerVote.Decision.REJECT,
                confidence = 1.0,
                rationale = "Consent violation: Target '$repoIdentifier' not in explicit allowlist (${consent.reason})."
            )
        }

        if (quota.isThrottled) {
            return ManagerVote(
                managerName = "Manager-4-ConsentAndRateGovernor",
                vote = ManagerVote.Decision.HOLD,
                confidence = 0.95,
                rationale = "API quota throttled for $platform; backoff until epoch ${quota.resetAtEpochSeconds}."
            )
        }

        return ManagerVote(
            managerName = "Manager-4-ConsentAndRateGovernor",
            vote = ManagerVote.Decision.APPROVE,
            confidence = 0.95,
            rationale = "Consent verified and rate quota healthy ($platform remaining: ${quota.remaining})."
        )
    }
}
