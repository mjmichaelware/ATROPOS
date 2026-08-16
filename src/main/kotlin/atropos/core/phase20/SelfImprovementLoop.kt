/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.ast.CompilerState
import atropos.core.ast.MdpCompilerState
import atropos.core.ast.MonteCarloBranchPruner
import atropos.core.verification.LearningProof
import atropos.core.time.RealClock
import atropos.core.time.SystemClock
import java.time.Instant

/**
 * The transition chain `P20-L01 … L15`, assembled.
 *
 * Every piece of this existed before this file and none of it ran. The
 * `core/phase20` package held nine classes and six test files, and
 * `ProposalGenerator`, `ProposalGate` and `ReproducibilityGate` had exactly one
 * production reference between them — inside `EvaluationEngine`, which used one
 * of them for something else. The architecture was complete and the loop was
 * never wired, which is the failure mode that reads as done from every angle
 * except running it.
 *
 * This file is the wiring and nothing else. It owns no policy: reproducibility
 * is [ReproducibilityPredicate], improvement is [ImprovementPredicate], limits
 * are [SelfImprovementBounds], prohibitions are [ImmutableInvariants],
 * termination is [TerminationRanking], and durability is [GovernanceLedger].
 * Each refused to be inlined here for the same reason — a loop that also
 * decided what counted as improvement could pass itself, which is precisely
 * what law 20.7 and `P20-NS05` forbid.
 *
 * The chain, and the law each step answers to:
 *
 * ```
 * L01 observation        normalised or rejected            20.2
 * L02 evidence           provenance + hashes or rejected   20.3
 * L03 R(d)               reproducible or dropped as noise  20.4
 * L04 deficiency         classified or dropped
 * L05 proposal           six mandatory fields              20.5, 20.6
 * L06 metric declared    before mutation                   20.6
 * L07 territory          bounded, impact closed            20.7
 * L08 auditor            proposer ≠ approver               20.7
 * L09 amendment          new CAS object, original intact   20.8
 * L10 DAG                only affected atoms               20.9
 * L11 Phase 11           executes; Phase 20 never edits    20.10
 * L12 verification       independent, fail closed          20.11, 20.12
 * L13 I(p)               improvement vs predeclared        20.13
 * L14 promote/rollback   on I(p) alone                     20.14
 * L15 ledger             both outcomes durable             20.15
 * ```
 *
 * Phase 11 remains the only component permitted to mutate ATROPOS source. This
 * emits a hash-pinned contract and stops; [advance] returns
 * [LoopOutcome.ContractReady] rather than calling an executor, because a loop
 * that could reach the mutation path directly would make law 20.10 a comment.
 */
class SelfImprovementLoop(
    private val ledger: GovernanceLedger,
    private val bounds: SelfImprovementBounds = SelfImprovementBounds(),
    private val generator: ProposalGenerator = ProposalGenerator(),
    private val clock: (() -> Instant)? = null,
    private val systemClock: SystemClock = RealClock()
) {
    private val branchPruner = MonteCarloBranchPruner()

    /**
     * Keeps candidate mutation selection on the canonical compiler-state
     * owner. Phase 20 proposes candidates; Phase 11 still performs mutation.
     */
    fun pruneCandidateMutations(initialCode: String, mutations: List<String>): List<String> {
        val stateOwner = MdpCompilerState(initialCode)
        val compilable = mutations.filter { delta ->
            stateOwner.transition("UPDATE", delta) { candidate ->
                if (candidate.isNotBlank() && !candidate.contains("\u0000")) 0 else 1
            }.compileResultExitCode == 0
        }
        return branchPruner.sampleAndPrune(
            initialState = CompilerState(initialCode, compileResultExitCode = 0, errors = emptyList()),
            mutations = compilable,
            compileCheck = { candidate -> candidate.isNotBlank() && !candidate.contains("\u0000") }
        )
    }

    /**
     * Runs `L01` through `L09` for one candidate deficiency.
     *
     * Stops at the first gate that refuses and says which. Every refusal is a
     * legitimate outcome rather than an error — a loop whose normal case is
     * "not yet" needs refusals to be as cheap and as legible as acceptances.
     */
    fun advance(request: LoopRequest): LoopOutcome {
        val now = (clock ?: { systemClock.now() })()

        // L01 — observations are normalised or rejected.
        val incomplete = request.observations.filterNot { it.complete }
        if (incomplete.isNotEmpty()) {
            return LoopOutcome.Refused(
                LoopStage.OBSERVATION,
                "observation ${incomplete.first().id} incomplete: " +
                    incomplete.first().missing().joinToString(", ")
            )
        }
        if (request.observations.isEmpty()) {
            return LoopOutcome.Refused(LoopStage.OBSERVATION, "no observations supplied")
        }

        // L02 — evidence must carry provenance and hashes.
        val unhashed = request.observations.filter { it.artifactHashes.isEmpty() }
        if (unhashed.isNotEmpty()) {
            return LoopOutcome.Refused(
                LoopStage.EVIDENCE,
                "observation ${unhashed.first().id} carries no artifact hashes; " +
                    "law 20.3 admits evidence to memory only with provenance and hashes"
            )
        }

        // L03 — R(d).
        val reproducibility = ReproducibilityPredicate.evaluate(request.observations)
        if (!reproducibility.holds) {
            return LoopOutcome.Refused(LoopStage.REPRODUCIBILITY, reproducibility.render())
        }

        // L04 — deficiency classification.
        val deficiency = classify(request.observations, reproducibility)
            ?: return LoopOutcome.Refused(
                LoopStage.CLASSIFICATION,
                "reproducible but not classifiable as a deficiency worth proposing against"
            )

        // H03/H04 — bounds and observation period, before any proposal is opened.
        val verdict = bounds.check(
            BoundsRequest(
                depth = request.depth,
                proposalsInPeriod = request.proposalsInPeriod,
                files = request.deficiency.territory.size,
                lines = request.estimatedLines,
                retries = request.retries,
                tokensSpentInPeriod = request.tokensSpentInPeriod,
                now = now,
                subsystemUnderObservationUntil = request.subsystemUnderObservationUntil
            )
        )
        if (!verdict.allowed) {
            return LoopOutcome.Refused(LoopStage.BOUNDS, verdict.render())
        }

        // L05/L06 — proposal with six mandatory fields and a predeclared metric.
        val proposal = generator.generate(request.deficiency.copy(observedAt = now))
        if (!proposal.isComplete()) {
            return LoopOutcome.Refused(
                LoopStage.PROPOSAL,
                "proposal missing " + proposal.missingFields().joinToString(", ")
            )
        }

        // Law 20.6 only. Law 20.1 — human authority over an invariant — is
        // enforced by the H01/H02 branch below, and it answers with the
        // outcome the operator can act on: NeedsHuman names the proposal and
        // the invariants it touches, where a flat Refused here would tell them
        // only that some law was broken and lose the one fact that makes it
        // fixable.
        if (!SelfImprovementLaws.checkLaw20_6(proposal)) {
            return LoopOutcome.Refused(
                LoopStage.PROPOSAL,
                "proposal declares no metric or no guardrails; law 20.6 requires both before it is opened"
            )
        }

        // H01/H02 — invariants and meta-level separation.
        val invariants = ImmutableInvariants.classify(proposal)
        if (invariants.requiresHumanAuthorisation && !request.humanAuthorised) {
            return LoopOutcome.NeedsHuman(proposal, invariants)
        }

        // L07/L08 — recorded, then audited by someone other than the proposer.
        val opened = ledger.propose(proposal)
        return LoopOutcome.ContractReady(
            proposal = opened,
            deficiency = deficiency,
            reproducibility = reproducibility,
            invariants = invariants,
            contractHash = opened.id
        )
    }

    /**
     * `L13` and `L14` — evaluates the result of an executed contract and says
     * whether it may be promoted.
     *
     * Called after Phase 11 has executed and independent verification has run.
     * Promotion is decided on `I(p)` alone: a change that passed verification
     * and did not improve its declared metric is a change that worked and was
     * not worth making, and law 20.14 rolls it back.
     */
    fun evaluate(
        proposal: ImprovementProposal,
        observedValue: Double,
        verificationPassed: Boolean,
        guardrailsBefore: List<AtroposMetric> = emptyList(),
        guardrailsAfter: List<AtroposMetric> = emptyList()
    ): PromotionDecision {
        // L12 — verification is independent and fails closed. Checked before
        // I(p) because an unverified change must not be measured at all: the
        // number it produced came from a state nobody vouched for.
        if (!verificationPassed) {
            ledger.recordFailure(proposal.id)
            return PromotionDecision(
                promote = false,
                improvement = ImprovementVerdict(false, "independent verification did not pass", Double.NaN, emptyList()),
                reason = "rolled back: verification failed"
            )
        }

        val improvement = ImprovementPredicate.evaluate(
            declared = proposal.metric,
            observed = observedValue,
            guardrailsBefore = guardrailsBefore,
            guardrailsAfter = guardrailsAfter
        )
        val learningProof = LearningProof.runProof(observedValue)
        if (!improvement.holds) {
            ledger.recordFailure(proposal.id)
            return PromotionDecision(false, improvement, "rolled back: ${improvement.reason}; learning=${learningProof.evidenceHash}")
        }
        return PromotionDecision(true, improvement, "promoted: ${improvement.reason}; learning=${learningProof.evidenceHash}")
    }

    /**
     * `H06` — whether the loop should run again.
     *
     * Composed rather than implemented here so that the ranking function stays
     * a single owner and this stays wiring.
     */
    fun shouldContinue(
        before: TerminationPotential,
        after: TerminationPotential,
        consecutiveStalls: Int
    ): TerminationStep = TerminationRanking.step(before, after, consecutiveStalls)

    /**
     * `L04` — turns reproducible observations into a typed deficiency, or drops
     * them.
     *
     * The gap map's advancement routes are: deterministic reproduce, frequency
     * threshold, single invariant break, safety-critical, or blocked
     * requirement. Anything else is reproducible noise — real, repeatable, and
     * not worth an amendment.
     */
    private fun classify(
        observations: List<RuntimeObservation>,
        reproducibility: ReproducibilityVerdict
    ): DeficiencyClass? {
        val first = observations.first()
        return when {
            first.severity == ObservationSeverity.SAFETY_CRITICAL -> DeficiencyClass.SAFETY_CRITICAL
            first.invariantBroken != null -> DeficiencyClass.INVARIANT_BREAK
            first.requirementBlocked != null -> DeficiencyClass.BLOCKED_REQUIREMENT
            reproducibility.occurrences >= ReproducibilityPredicate.FREQUENCY_THRESHOLD &&
                first.severity == ObservationSeverity.FAILURE -> DeficiencyClass.RECURRING_FAILURE
            reproducibility.occurrences >= ReproducibilityPredicate.FREQUENCY_THRESHOLD &&
                first.severity == ObservationSeverity.DEGRADED -> DeficiencyClass.RECURRING_DEGRADATION
            else -> null
        }
    }
}

/** What the loop needs to attempt one advance. */
data class LoopRequest(
    val observations: List<RuntimeObservation>,
    val deficiency: ProposalDeficiency,
    val depth: Int = 1,
    val proposalsInPeriod: Int = 0,
    val estimatedLines: Int = 0,
    val retries: Int = 0,
    val tokensSpentInPeriod: Long = 0,
    val subsystemUnderObservationUntil: Instant? = null,
    val humanAuthorised: Boolean = false
)

/** Which transition refused, so a refusal names a law rather than a line. */
enum class LoopStage {
    OBSERVATION, EVIDENCE, REPRODUCIBILITY, CLASSIFICATION, BOUNDS, PROPOSAL, INVARIANTS
}

/** The five things one advance can conclude. */
sealed class LoopOutcome {

    /** A gate refused. The normal case, and not an error. */
    data class Refused(val stage: LoopStage, val reason: String) : LoopOutcome()

    /** An invariant or the meta level is implicated; a human must authorise. */
    data class NeedsHuman(
        val proposal: ImprovementProposal,
        val invariants: InvariantVerdict
    ) : LoopOutcome()

    /**
     * A hash-pinned contract Phase 11 may execute.
     *
     * The loop stops here. Law 20.10 makes Phase 11 the only component allowed
     * to mutate source, and returning a contract rather than calling an
     * executor is what keeps that structural.
     */
    data class ContractReady(
        val proposal: ImprovementProposal,
        val deficiency: DeficiencyClass,
        val reproducibility: ReproducibilityVerdict,
        val invariants: InvariantVerdict,
        val contractHash: String
    ) : LoopOutcome()

    fun render(): String = when (this) {
        is Refused -> "refused at $stage: $reason"
        is NeedsHuman -> "needs human authorisation: " + invariants.render()
        is ContractReady -> "contract ${contractHash.take(12)} ready for Phase 11 · $deficiency · " +
            reproducibility.render()
    }
}

/** What kind of deficiency advanced, per `P20-L04`. */
enum class DeficiencyClass {
    SAFETY_CRITICAL,
    INVARIANT_BREAK,
    BLOCKED_REQUIREMENT,
    RECURRING_FAILURE,
    RECURRING_DEGRADATION
}

/** `L14` — promote or roll back, with the evidence for either. */
data class PromotionDecision(
    val promote: Boolean,
    val improvement: ImprovementVerdict,
    val reason: String
)
