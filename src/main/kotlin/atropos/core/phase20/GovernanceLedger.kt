/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.AtroposRepoRootLocator
import atropos.core.evaluation.EvidenceStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant
import atropos.core.verification.Proposal as LegacyProposal
import atropos.core.verification.ProposalSixFields
import java.util.concurrent.atomic.AtomicLong

/**
 * The durable Phase 20 record.
 *
 * Until this existed, every Phase 20 type in the system was reachable only from
 * a test. The gate could refuse, the metrics could divide, the amendment could
 * hash — and nothing wrote any of it down, so `/governance` truthfully rendered
 * "no proposals" forever. That is a defensible empty state and an indefensible
 * permanent one: a governance surface that structurally cannot fill is theatre.
 *
 * Append-only, like the approval log and the Progress Ledger, and for the same
 * reason. §20.1 makes original authority immutable; a ledger whose earlier lines
 * could be rewritten would let an accepted amendment quietly change what it
 * superseded, which is the one thing the whole phase exists to prevent. A state
 * change is a new line carrying the same id, and the last line wins on read.
 *
 * The ledger decides nothing on its own. [ProposalGate] owns the law and this
 * owns durability — so a refusal is always traceable to a §-numbered rule
 * rather than to a storage detail, and the gate stays testable without a disk.
 */
class GovernanceLedger(
    repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val codec: GovernanceLedgerCodec = GovernanceLedgerCodec(),
    private val gate: ProposalGate = ProposalGate(),
    private val antiGamingAuditor: AntiGamingAuditor = AntiGamingAuditor(),
    private val proposalGenerator: ProposalGenerator = ProposalGenerator(),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val file: Path = repoRoot.resolve(".atropos/governance/ledger.log").normalize()
    private val counter = AtomicLong(0)
    private val evidenceStore = EvidenceStore(repoRoot)
    private val evidenceLedger = EvidenceLedger(evidenceStore)
    private val proposalStore = ProposalStore(evidenceStore)
    private val memoryLedger = MemoryLedger(evidenceStore)
    private val amendmentRegistry = AmendmentRegistry(evidenceStore)
    private val lakehouseRetrieve = LakehouseRetrieve(evidenceStore)
    private val selfBuildValidationRule = SelfBuildValidationRule(Phase20Laws())
    private val observationCasLedger = ObservationCasLedger(evidenceStore)
    private val evidenceCasLedger = EvidenceCasLedger(evidenceStore)
    private val proposalCasLedger = ProposalCasLedger(evidenceStore)
    private val amendmentCasLedger = AmendmentCasLedger(evidenceStore)
    private val selfImprovementLoop = SelfImprovementLoop(this)

    /** Enters the canonical Phase 20 loop through the durable governance owner. */
    fun advanceSelfImprovement(request: LoopRequest): LoopOutcome = selfImprovementLoop.advance(request)

    /** Evaluates a completed Phase 11 contract without granting Phase 20 a mutation path. */
    fun evaluateSelfImprovement(
        proposal: ImprovementProposal,
        observedValue: Double,
        verificationPassed: Boolean,
        guardrailsBefore: List<atropos.core.evaluation.AtroposMetric> = emptyList(),
        guardrailsAfter: List<atropos.core.evaluation.AtroposMetric> = emptyList()
    ): PromotionDecision = selfImprovementLoop.evaluate(
        proposal,
        observedValue,
        verificationPassed,
        guardrailsBefore,
        guardrailsAfter
    )

    /** Runs the canonical governance detector registry for an observed run. */
    fun detectObservations(context: GovernanceDetectorContext): List<RuntimeObservation> =
        GovernanceDetectorsRegistry.runAll(context)

    /** Stores an evidence manifest in the shared governance CAS. */
    fun storeEvidenceManifest(manifest: StructuralManifest): String =
        evidenceLedger.storeManifest(manifest)

    /** Builds a manifest using the same byte-offset convention as the ledger. */
    fun manifest(documentHash: String): ManifestBuilder = ManifestBuilder(documentHash)

    /** Retrieves a previously stored evidence region from the shared CAS. */
    fun retrieveEvidenceRegion(manifest: StructuralManifest, regionIndex: Int): String? =
        lakehouseRetrieve.retrieveRegion(manifest, regionIndex)

    /** Stores a memory snapshot against the shared evidence CAS. */
    fun storeMemorySnapshot(content: String, manifest: StructuralManifest): Pair<String, String> =
        memoryLedger.storeMemory(content, manifest)

    /** Stores a proposal manifest against the shared evidence CAS. */
    fun storeProposalManifest(content: String, manifest: StructuralManifest): Pair<String, String> =
        proposalStore.storeProposal(content, manifest)

    /** Stores an accepted amendment without creating a second authority store. */
    fun storeAmendmentManifest(
        content: String,
        manifest: StructuralManifest,
        supersedesHash: String?
    ): Pair<String, String> = amendmentRegistry.registerAmendment(content, manifest, supersedesHash)

    /** Applies the canonical Phase 20 law checks before self-build promotion. */
    fun validateSelfBuildPromotion(
        callerComponent: String,
        proposerId: String,
        evaluatorId: String,
        oldComplianceScore: Int,
        newComplianceScore: Int,
        compileExitCode: Int,
        testExitCode: Int,
        targetClaim: ClaimLevel
    ) = selfBuildValidationRule.validateAmendmentPromotion(
        callerComponent,
        proposerId,
        evaluatorId,
        oldComplianceScore,
        newComplianceScore,
        compileExitCode,
        testExitCode,
        targetClaim
    )

    /**
     * Records a proposal.
     *
     * A structurally incomplete proposal is written anyway, in its incomplete
     * state, and refused at acceptance. That is deliberate: §20.6 makes the six
     * declarations a condition of *acceptance*, and discarding the attempt here
     * would erase the evidence that the system keeps proposing under-specified
     * changes — which `P20-G08` treats as a governance deficiency in its own
     * right. The surface already renders such a proposal as "not approvable"
     * with the missing fields named.
     */
    fun propose(proposal: ImprovementProposal): ImprovementProposal {
        existing(proposal.id)?.let { return it }
        append(codec.encodeProposal(proposal))
        return proposal
    }

    /** Compatibility ingress; all legacy proposals enter the canonical ledger. */
    fun propose(
        proposal: LegacyProposal,
        proposedBy: String,
        now: Instant = clock()
    ): ImprovementProposal? = ProposalSixFields.toCanonical(proposal, proposedBy, now)?.let(::propose)

    /** Generates and records a proposal without bypassing ledger durability. */
    fun propose(deficiency: ProposalDeficiency): ImprovementProposal =
        propose(proposalGenerator.generate(deficiency))

    /** Convenience for callers that have the declarations but not an id. */
    fun propose(
        proposedBy: String,
        summary: String,
        necessity: List<String>,
        baseline: String,
        target: String,
        guardrails: List<String>,
        territory: List<String>,
        risk: String,
        rollback: String,
        metric: MetricDeclaration
    ): ImprovementProposal {
        val createdAt = clock()
        return propose(
            ImprovementProposal(
                id = "prop-${createdAt.toEpochMilli()}-${counter.incrementAndGet()}",
                proposedBy = proposedBy,
                summary = summary,
                necessity = necessity,
                baseline = baseline,
                target = target,
                guardrails = guardrails,
                territory = territory,
                risk = risk,
                rollback = rollback,
                metric = metric,
                createdAt = createdAt
            )
        )
    }

    /** Audits a proposed metric against the independently measured outcome. */
    fun auditProposal(
        proposalId: String,
        observedDeclaredMetric: Double,
        outcomeMetric: MetricDeclaration,
        observedOutcome: Double,
        evidenceHashes: List<String>
    ): AntiGamingDecision {
        val proposal = existing(proposalId)
            ?: return AntiGamingDecision(false, "no proposal with id $proposalId")
        return antiGamingAuditor.audit(
            proposal,
            AntiGamingEvidence(
                observedDeclaredMetric = observedDeclaredMetric,
                outcomeMetric = outcomeMetric,
                observedOutcome = observedOutcome,
                evidenceHashes = evidenceHashes
            )
        )
    }

    /**
     * Accepts a proposal, writing an amendment and opening its observation period.
     *
     * The gate is consulted first and its refusal is returned verbatim, law
     * included. Nothing is written when the gate refuses — a rejected proposal
     * that left an amendment behind would be authority nobody approved.
     *
     * The observation period opens in the same call rather than being left to
     * the caller. §20.14 makes it a condition of the promotion, and a caller
     * that forgot to start it would produce a subsystem that could be changed
     * again immediately while the ledger showed a compliant acceptance.
     */
    fun accept(
        proposalId: String,
        acceptedBy: String,
        supersedes: String,
        evidenceHashes: List<String>,
        observationSeconds: Long = DEFAULT_OBSERVATION_SECONDS,
        humanAuthorised: Boolean = false
    ): LedgerOutcome {
        val proposal = existing(proposalId)
            ?: return LedgerOutcome.Refused("20.6", "no proposal with id $proposalId")

        if (evidenceHashes.isEmpty()) {
            // §20.19: a completion claim cites its evidence. An amendment is
            // the strongest claim the system can make about itself.
            return LedgerOutcome.Refused(
                "20.19",
                "an amendment must cite the evidence hashes that justify it"
            )
        }
        if (supersedes.isBlank()) {
            return LedgerOutcome.Refused(
                "20.8",
                "an amendment must name the authority hash it supersedes"
            )
        }

        val now = clock()
        val decision = gate.evaluate(
            proposal = proposal,
            approver = acceptedBy,
            openPeriods = openObservationPeriods(now),
            now = now,
            humanAuthorised = humanAuthorised
        )
        if (decision is ProposalDecision.Refused) {
            return LedgerOutcome.Refused(decision.law, decision.reason)
        }

        val amendment = AuthorityAmendment(
            id = "amd-${now.toEpochMilli()}-${counter.incrementAndGet()}",
            proposalId = proposal.id,
            sha256 = amendmentHash(proposal, acceptedBy, evidenceHashes),
            supersedes = supersedes,
            acceptedBy = acceptedBy,
            acceptedAt = now,
            evidenceHashes = evidenceHashes
        )

        append(codec.encodeProposal(proposal.copy(state = ProposalState.ACCEPTED)))
        append(codec.encodeAmendment(amendment))
        proposal.territory.forEach { subsystem ->
            append(codec.encodeObservation(ObservationPeriod(subsystem, now, observationSeconds)))
        }
        return LedgerOutcome.Accepted(amendment)
    }

    /**
     * Records a failure against a proposal, quarantining it once §20.16's
     * threshold is reached.
     *
     * Counted rather than inferred: a proposal that has failed three times and
     * one that was tried once are indistinguishable from the final state alone,
     * and the difference is precisely what quarantine is keyed on.
     */
    fun recordFailure(proposalId: String): LedgerOutcome {
        val proposal = existing(proposalId)
            ?: return LedgerOutcome.Refused("20.6", "no proposal with id $proposalId")
        val failures = proposal.failureCount + 1
        val quarantined = failures >= QUARANTINE_AFTER_FAILURES
        val updated = proposal.copy(
            failureCount = failures,
            state = if (quarantined) ProposalState.QUARANTINED else proposal.state
        )
        append(codec.encodeProposal(updated))
        return LedgerOutcome.Recorded(updated)
    }

    fun reject(proposalId: String, reason: String): LedgerOutcome {
        val proposal = existing(proposalId)
            ?: return LedgerOutcome.Refused("20.6", "no proposal with id $proposalId")
        if (reason.isBlank()) {
            return LedgerOutcome.Refused("20.20", "a rejection must state why")
        }
        val updated = proposal.copy(state = ProposalState.REJECTED)
        append(codec.encodeProposal(updated))
        return LedgerOutcome.Recorded(updated)
    }

    /** Proposals, latest line per id, oldest first. */
    fun proposals(): List<ImprovementProposal> {
        val byId = LinkedHashMap<String, ImprovementProposal>()
        lines().forEach { line ->
            codec.decodeProposal(line)?.let { byId[it.id] = it }
        }
        return byId.values.toList()
    }

    fun amendments(): List<AuthorityAmendment> =
        lines().mapNotNull(codec::decodeAmendment)

    /**
     * Observation periods, latest per subsystem.
     *
     * Superseding by subsystem rather than accumulating: a subsystem changed
     * twice has one current period, and summing the old ones would keep it
     * frozen long after §20.14 was satisfied.
     */
    fun observationPeriods(): List<ObservationPeriod> {
        val bySubsystem = LinkedHashMap<String, ObservationPeriod>()
        lines().forEach { line ->
            codec.decodeObservation(line)?.let { bySubsystem[it.subsystem] = it }
        }
        return bySubsystem.values.toList()
    }

    fun openObservationPeriods(now: Instant = clock()): List<ObservationPeriod> =
        observationPeriods().filter { it.isOpenAt(now) }

    /**
     * The counts `P20-S04`'s metrics divide.
     *
     * Only the two this ledger actually observes are filled in. The rest stay
     * zero so their rates stay null — reporting a false-VERIFIED rate this
     * ledger has no way to measure would be inventing a measurement, and the
     * metric type treats a zero denominator as "not measured" precisely so that
     * absence survives to the surface.
     */
    fun counts(now: Instant = clock()): GovernanceCounts {
        val periods = observationPeriods()
        return GovernanceCounts(
            observationPeriods = periods.size.toLong(),
            observationsSurvived = periods.count { !it.isOpenAt(now) }.toLong()
        )
    }

    private fun existing(id: String): ImprovementProposal? = proposals().lastOrNull { it.id == id }

    /**
     * The amendment's own hash.
     *
     * Derived from the proposal, the approver and the cited evidence, so two
     * acceptances of the same proposal by different people hash differently.
     * §20.8 requires the amendment's hash to be independent of the source
     * document's, which it is — nothing here reads authority text.
     */
    private fun amendmentHash(
        proposal: ImprovementProposal,
        acceptedBy: String,
        evidenceHashes: List<String>
    ): String {
        val material = buildString {
            append(proposal.id).append('\n')
            append(proposal.summary).append('\n')
            append(proposal.rollback).append('\n')
            append(proposal.territory.sorted().joinToString(",")).append('\n')
            append(acceptedBy).append('\n')
            append(evidenceHashes.sorted().joinToString(","))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun lines(): List<String> {
        if (!Files.isRegularFile(file)) return emptyList()
        return try {
            Files.readAllLines(file, StandardCharsets.UTF_8).filter { it.isNotBlank() }
        } catch (_: Exception) {
            // An unreadable ledger is not an empty one, but a read that throws
            // would take the whole governance surface down; the surface reports
            // the engine's refusal instead.
            emptyList()
        }
    }

    private fun append(line: String) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            line + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }

    companion object {
        /** §20.16's threshold, matching [ProposalGate]'s default. */
        const val QUARANTINE_AFTER_FAILURES = 3

        /** §20.14: long enough that a regression has a chance to appear. */
        const val DEFAULT_OBSERVATION_SECONDS = 24L * 60 * 60
    }
}

sealed class LedgerOutcome {
    data class Accepted(val amendment: AuthorityAmendment) : LedgerOutcome()
    data class Recorded(val proposal: ImprovementProposal) : LedgerOutcome()

    /** Every refusal names the law that produced it. */
    data class Refused(val law: String, val reason: String) : LedgerOutcome()
}
