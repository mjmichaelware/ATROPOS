/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric

/**
 * Enforced predicates for Phase 20 self-improvement loop laws (Laws 20.1 - 20.8).
 * Ensures mathematical and structural invariants are validated at each loop transition.
 */
object SelfImprovementLaws {

    /** Law 20.1: Human authority takes precedence over autonomous changes. */
    fun checkLaw20_1(proposal: ImprovementProposal, humanAuthorized: Boolean): Boolean {
        val verdict = ImmutableInvariants.classify(proposal)
        return !verdict.requiresHumanAuthorisation || humanAuthorized
    }

    /** Law 20.2: Observations must be normalized into structured tuples. */
    fun checkLaw20_2(observation: RuntimeObservation): Boolean {
        return observation.complete && observation.id.isNotBlank() && observation.runtimeId.isNotBlank()
    }

    /** Law 20.3: Evidence admitted to memory only with provenance, timestamps, and hashes. */
    fun checkLaw20_3(observation: RuntimeObservation): Boolean {
        return observation.artifactHashes.isNotEmpty() && observation.goalId != null
    }

    /** Law 20.4: SAFETY-CRITICAL reproduce on first occurrence; others require frequency threshold R(d). */
    fun checkLaw20_4(observations: List<RuntimeObservation>): Boolean {
        if (observations.isEmpty()) return false
        val first = observations.first()
        if (first.severity == ObservationSeverity.SAFETY_CRITICAL) return true
        val reproducibility = ReproducibilityPredicate.evaluate(observations)
        return reproducibility.holds
    }

    /** Law 20.5: Proposals must specify deficiency, target, guardrails, estimated cost, and code delta. */
    fun checkLaw20_5(proposal: ImprovementProposal): Boolean {
        return proposal.isComplete()
    }

    /** Law 20.6: Target metric and guardrails must be declared *before* code mutation starts. */
    fun checkLaw20_6(proposal: ImprovementProposal): Boolean {
        return proposal.metric.name.isNotBlank() && proposal.guardrails.isNotEmpty()
    }

    /** Law 20.7: Proposer cannot audit or approve their own proposals. */
    fun checkLaw20_7(proposer: String, auditor: String): Boolean {
        return proposer.isNotBlank() && auditor.isNotBlank() && proposer != auditor
    }

    /** Law 20.8: Amendments are appended to content-addressed ledger, original baseline remains immutable. */
    fun checkLaw20_8(amendment: AuthorityAmendment): Boolean {
        return amendment.id.isNotBlank() && amendment.proposalId.isNotBlank() && amendment.sha256.isNotBlank()
    }

    /** Law 20.9: DAG execution restricted to affected atoms only. */
    fun checkLaw20_9(proposal: ImprovementProposal, executionScope: List<String>): Boolean {
        // All execution scopes must be within the proposal's declared territory.
        if (executionScope.isEmpty()) return false
        return executionScope.all { scope ->
            proposal.territory.any { t -> scope.startsWith(t) || t.startsWith(scope) }
        }
    }

    /** Law 20.10: Phase 11 is the only mutation path; Phase 20 never edits source directly. */
    fun checkLaw20_10(mutationPath: String): Boolean {
        return mutationPath == "PHASE_11_SELF_BUILD"
    }

    /** Law 20.11: Independent verification required (verifier != proposer). */
    fun checkLaw20_11(proposer: String, verifier: String): Boolean {
        return proposer.isNotBlank() && verifier.isNotBlank() && proposer != verifier
    }

    /** Law 20.12: Verification fails closed (ambiguous = fail). */
    fun checkLaw20_12(verificationResult: String): Boolean {
        return verificationResult == "VERIFIED"
    }

    /** Law 20.13: Improvement predicate I(p) evaluated against pre-declared metric. */
    fun checkLaw20_13(proposal: ImprovementProposal, observedValue: Double): Boolean {
        return proposal.metric.improvedBy(observedValue)
    }

    /** Law 20.14: Promotion/rollback decided on I(p) alone. */
    fun checkLaw20_14(improved: Boolean, action: String): Boolean {
        return if (improved) action == "PROMOTE" else action == "ROLLBACK"
    }

    /** Law 20.15: Both outcomes (promote and rollback) must be durably recorded. */
    fun checkLaw20_15(recordType: String): Boolean {
        return recordType == "PROMOTION_RECORD" || recordType == "ROLLBACK_RECORD"
    }

    /** Law 20.16: Safety hard-fail (if guardrail broken -> rollback). */
    fun checkLaw20_16(guardrailsBroken: Boolean, action: String): Boolean {
        if (guardrailsBroken) {
            return action == "ROLLBACK"
        }
        return true // if not broken, could be PROMOTE or ROLLBACK based on other factors
    }

    /** Law 20.17: Evidence-only completion (completion claimed only with evidence). */
    fun checkLaw20_17(claimedComplete: Boolean, evidenceHashes: List<String>): Boolean {
        if (claimedComplete) {
            return evidenceHashes.isNotEmpty()
        }
        return true
    }

    /** Law 20.18: Anti-gaming (cannot modify the evaluation metric during execution). */
    fun checkLaw20_18(originalMetric: MetricDeclaration, currentMetric: MetricDeclaration): Boolean {
        return originalMetric == currentMetric
    }

    /** Law 20.19: Attestation (all automated actions signed by the agent). */
    fun checkLaw20_19(agentSignature: String): Boolean {
        return agentSignature.isNotBlank() && agentSignature.length >= 32
    }

    /**
     * Law 20.20: dependencies must be hash-pinned.
     *
     * The `|| it.contains(":")` this replaces made the law unfalsifiable. Every
     * package URI contains a colon — `pkg:github/repo@latest` does — so a
     * floating tag passed as readily as a digest, and the one thing the law
     * exists to catch was the one thing it could not see.
     *
     * A pin is a digest: an algorithm name followed by hex. Anything else is a
     * name that can be repointed after the proposal was audited, which is
     * precisely the substitution the law forbids.
     */
    fun checkLaw20_20(dependencies: List<String>): Boolean =
        dependencies.isNotEmpty() && dependencies.all(DIGEST_PIN::containsMatchIn)

    /**
     * `sha256:<64 hex>`, and the same shape for sha384 and sha512.
     *
     * The length is checked per algorithm rather than loosely, because a
     * truncated digest is not a pin: `sha256:1234` names a prefix that many
     * different artifacts share, so an auditor who approved one of them has
     * approved all of them.
     */
    private val DIGEST_PIN = Regex(
        "sha256[:=-][0-9a-fA-F]{64}|sha384[:=-][0-9a-fA-F]{96}|sha512[:=-][0-9a-fA-F]{128}"
    )
}
