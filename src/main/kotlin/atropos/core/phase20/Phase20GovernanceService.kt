/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.EvidenceStore
import java.time.Instant

/**
 * One governance pass: observe the runtime, gate it, and say what may advance.
 *
 * `/agent self-host governance` used to answer this question with a constant —
 * it printed "All safety-critical invariants checked: passed=true" without
 * checking anything, which is the failure §0.6 exists to forbid, and it read as
 * a passing system from every angle except running the checks. Everything
 * needed to answer it honestly was already in `core/phase20` and unreachable:
 * nine detectors, a policy gate, the L01–L15 loop, four CAS ledgers and an
 * audit log, none of them with a production caller.
 *
 * This is the caller. Per §0.7 it composes existing owners and owns no policy
 * of its own — detection is [GovernanceDetectorsRegistry], limits are
 * [PolicyGate], the transition chain is [SelfImprovementLoop], the laws are
 * [SelfImprovementLaws], durability is [GovernanceLedger] and the CAS ledgers
 * in [LakehouseLedgers]. It decides only the order they run in and what a
 * refusal at each step means for the pass as a whole.
 *
 * The one thing it does add is a *shared* [EvidenceStore]. Every CAS ledger in
 * this package defaults to constructing its own, which is what made them
 * unusable together: two ledgers built separately write to two stores, so a
 * hash handed out by one resolves to nothing in the other. Passing one instance
 * to all of them is the whole fix — no new store, no new retrieval surface.
 *
 * Refusal is the expected outcome. A pass that finds nothing to propose has
 * done its job; [GovernanceReport.render] says so in those terms rather than
 * reporting a success that implies a change was made.
 */
class Phase20GovernanceService(
    private val ledger: GovernanceLedger = GovernanceLedger(),
    /** Shared by every CAS ledger below, so one hash means one object. */
    private val evidence: EvidenceStore = EvidenceStore(),
    private val policyGate: PolicyGate = PolicyGate(),
    private val auditLog: Phase20AuditLog = Phase20AuditLog(),
    private val loop: SelfImprovementLoop = Phase20Loop.canonical(ledger),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val observationLedger = ObservationCasLedger(evidence)
    private val proposalLedger = ProposalCasLedger(evidence)
    private val evidenceLedger = EvidenceCasLedger(evidence)


    /**
     * Observes and gates, stopping before any proposal is opened.
     *
     * The read-only half, and what `/agent self-host governance` calls. It
     * cannot open a proposal, so it cannot consume the rate limit that a later
     * real one will need.
     */
    fun observe(context: GovernanceDetectorContext, policy: PolicyGateContext = PolicyGateContext()): GovernanceReport {
        val now = clock()
        val detected = GovernanceDetectorsRegistry.runAll(context)

        // ObservationCasLedger.store requires completeness and throws
        // otherwise. A pass runs over whatever the detectors produced, and one
        // incomplete observation must not abort it — the incompleteness is
        // itself a finding, reported below rather than thrown here.
        val stored = detected
            .filter { it.complete }
            .associate { it.id to observationLedger.store(it) }

        // An observation the detectors produced but the ledger refused is a
        // finding in its own right: something is emitting observations that do
        // not satisfy law 20.2, and reporting the pass as clean would hide it.
        val incomplete = detected.filter { it.id !in stored }

        val lawFindings = buildList {
            InvariantContractCatalog.evaluate(InvariantContractCatalog.from(context)).forEach { violation ->
                add("${violation.id}: missing ${violation.missingFact}")
            }
            detected.forEach { observation ->
                if (!SelfImprovementLaws.checkLaw20_2(observation)) {
                    add("20.2 ${observation.id}: not normalised into a complete tuple")
                }
                if (!SelfImprovementLaws.checkLaw20_3(observation)) {
                    add("20.3 ${observation.id}: admitted without provenance or artifact hashes")
                }
            }
            if (detected.isNotEmpty() && !SelfImprovementLaws.checkLaw20_4(detected)) {
                add("20.4: not reproducible and not safety-critical — no advancement route")
            }
        }

        val verdict = policyGate.evaluate(policy, now)

        // P20-SUP: the superiority primitives, reported rather than enforced.
        // They describe how strong the evidence is, which is a different
        // question from whether the gate allows a change, and collapsing the
        // two would let a weak-but-permitted pass look like a strong one.
        val reproducibility = FormalReproducibility.evaluate(detected)
        val metaLevel = ObjectMetaSeparation.isMetaLevel(context.territory)

        return GovernanceReport(
            at = now,
            observations = detected,
            observationHashes = stored,
            incompleteObservations = incomplete.map { it.id },
            lawFindings = lawFindings,
            policy = verdict,
            reproducibilityScore = reproducibility,
            targetsMetaLevel = metaLevel,
            outcome = null
        )
    }

    /**
     * A full pass: observe, gate, then run the transition chain for [deficiency].
     *
     * Stops at [LoopOutcome.ContractReady]; law 20.10 makes Phase 11 the only
     * component permitted to mutate source, so this returns a contract and
     * never executes one.
     */
    fun advance(
        context: GovernanceDetectorContext,
        deficiency: ProposalDeficiency,
        policy: PolicyGateContext = PolicyGateContext(),
        humanAuthorised: Boolean = false
    ): GovernanceReport {
        val report = observe(context, policy)
        if (!report.policy.allowed) {
            record(report, "advance-refused", report.policy.reason)
            return report
        }

        val outcome = loop.advance(
            LoopRequest(
                observations = report.observations,
                deficiency = deficiency,
                depth = policy.depth,
                proposalsInPeriod = policy.proposalsInPeriod,
                estimatedLines = policy.linesChanged,
                retries = policy.retries,
                tokensSpentInPeriod = policy.tokensSpentInPeriod,
                subsystemUnderObservationUntil = policy.subsystemUnderObservationUntil,
                humanAuthorised = humanAuthorised
            )
        )

        // A proposal is durable before it is acted on, not after. Recording it
        // afterwards means a crash between the two loses the record of a
        // proposal the ledger has already opened.
        if (outcome is LoopOutcome.ContractReady) {
            proposalLedger.store(outcome.proposal)
        }

        val advanced = report.copy(outcome = outcome)
        record(advanced, "advance", outcome.render())
        return advanced
    }

    /**
     * `L13`/`L14` — decides promotion for an executed contract, and rolls back
     * when the declared metric did not move.
     *
     * @param rollback the deployment register. Passed in because what is
     *   currently deployed is process state, not something this service may
     *   invent: a rollback register constructed here would start empty and
     *   report every live amendment as absent.
     */
    fun promote(
        proposal: ImprovementProposal,
        observedValue: Double,
        verificationPassed: Boolean,
        rollback: SelfImprovementRollback,
        guardrailsBefore: List<AtroposMetric> = emptyList(),
        guardrailsAfter: List<AtroposMetric> = emptyList()
    ): PromotionDecision {
        val decision = loop.evaluate(
            proposal = proposal,
            observedValue = observedValue,
            verificationPassed = verificationPassed,
            guardrailsBefore = guardrailsBefore,
            guardrailsAfter = guardrailsAfter
        )

        if (decision.promote) {
            rollback.deployAmendment(proposal.id)
        } else {
            rollback.triggerRollback(proposal.id, decision.reason)
        }

        auditLog.append(
            AuditEvent(
                eventId = "promote-${proposal.id}",
                proposalId = proposal.id,
                timestamp = clock(),
                action = if (decision.promote) "promote" else "rollback",
                agentId = proposal.proposedBy,
                result = decision.reason,
                // The signature is the CAS address of the decision text. It is
                // not a claim of authorship — it is what makes the audit line
                // checkable: the reason recorded here either hashes to this or
                // the log has been edited.
                cryptographicSignature = evidenceLedger.store(decision.reason)
            )
        )
        return decision
    }

    /** Everything the audit log holds for one proposal, oldest first. */
    fun auditTrail(proposalId: String): List<AuditEvent> = auditLog.getLogForProposal(proposalId)

    private fun record(report: GovernanceReport, action: String, result: String) {
        auditLog.append(
            AuditEvent(
                eventId = "$action-${report.at.toEpochMilli()}",
                proposalId = (report.outcome as? LoopOutcome.ContractReady)?.proposal?.id ?: "none",
                timestamp = report.at,
                action = action,
                agentId = "phase20-governance",
                result = result,
                cryptographicSignature = evidenceLedger.store(report.render())
            )
        )
    }
}

/**
 * What one pass found.
 *
 * Every field is something that was measured. There is no aggregate "passed"
 * flag: the question "did governance pass" has no single answer — a pass can
 * find no deficiency (fine), find one and be rate-limited (fine, later), or
 * find a law broken (not fine), and flattening those into a boolean is what
 * produced the constant `passed=true` this replaced.
 */
data class GovernanceReport(
    val at: Instant,
    val observations: List<RuntimeObservation>,
    /** Observation id to CAS hash, for the ones that were storable. */
    val observationHashes: Map<String, String>,
    val incompleteObservations: List<String>,
    val lawFindings: List<String>,
    val policy: PolicyGateVerdict,
    /** `R(d)` over the observations, 0.0 to 1.0. */
    val reproducibilityScore: Double,
    /** Whether the declared territory reaches the meta level, per `P20-SUP`. */
    val targetsMetaLevel: Boolean,
    val outcome: LoopOutcome?
) {
    /** True only when nothing broke a law and the gate allowed the pass. */
    val clean: Boolean get() = lawFindings.isEmpty() && policy.allowed

    fun render(): String = buildString {
        appendLine("PHASE 20 GOVERNANCE — $at")
        appendLine("  observations   ${observations.size} detected, ${observationHashes.size} recorded")
        if (incompleteObservations.isNotEmpty()) {
            appendLine("  incomplete     ${incompleteObservations.joinToString(", ")}")
        }
        appendLine("  reproducibility ${"%.2f".format(reproducibilityScore)}")
        if (targetsMetaLevel) {
            appendLine("  meta level     territory reaches the meta level; human authority required")
        }
        appendLine("  policy         ${policy.reason}")
        policy.violations.forEach { appendLine("    $it") }
        if (lawFindings.isEmpty()) {
            appendLine("  laws           no violation found in ${observations.size} observation(s)")
        } else {
            appendLine("  laws           ${lawFindings.size} violation(s)")
            lawFindings.forEach { appendLine("    $it") }
        }
        outcome?.let { appendLine("  loop           ${it.render()}") }
            ?: appendLine("  loop           not advanced (observation pass only)")
    }.trimEnd()
}
