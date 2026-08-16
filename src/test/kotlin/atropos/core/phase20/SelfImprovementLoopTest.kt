/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.MetricId
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The loop's job is mostly to refuse. Every one of these is a case where an
 * unwired or naive implementation would have proceeded — and proceeding is how
 * a self-improvement loop turns a transient failure into an amendment to its
 * own authority.
 */
class SelfImprovementLoopTest {

    private val at = Instant.parse("2026-08-13T12:00:00Z")

    private fun ledger() = GovernanceLedger(
        repoRoot = Files.createTempDirectory("atropos-p20-"),
        clock = { at }
    )

    private fun observation(
        id: String = "obs-1",
        severity: ObservationSeverity = ObservationSeverity.FAILURE,
        frequency: Int = 3,
        environment: String = "termux-aarch64",
        hashes: List<String> = listOf("a".repeat(64)),
        invariant: String? = null,
        output: String = "compile failed at line 12"
    ) = RuntimeObservation(
        id = id,
        timestamp = at,
        runtimeId = "runtime-1",
        projectId = "atropos",
        goalId = "goal-1",
        nodeId = "node-1",
        authorityFingerprint = "auth-abc",
        environmentFingerprint = environment,
        exitCode = 1,
        boundedOutput = output,
        artifactHashes = hashes,
        frequency = frequency,
        severity = severity,
        invariantBroken = invariant
    )

    private fun deficiency(territory: List<String> = listOf("src/main/kotlin/atropos/core/x")) =
        ProposalDeficiency(
            proposedBy = "auditor",
            summary = "compile repair loop retries without bound",
            necessity = listOf("three identical failures across two environments"),
            baseline = "retry_rate=0.4",
            target = "retry_rate=0.1",
            guardrails = listOf("territory unchanged"),
            territory = territory,
            risk = "low",
            rollback = "revert the batch",
            metric = MetricDeclaration("retry_rate", 0.4, 0.1, lowerIsBetter = true),
            observedAt = at
        )

    private fun request(
        observations: List<RuntimeObservation> = listOf(observation()),
        deficiency: ProposalDeficiency = deficiency(),
        humanAuthorised: Boolean = false,
        depth: Int = 1,
        proposalsInPeriod: Int = 0,
        observationUntil: Instant? = null
    ) = LoopRequest(
        observations = observations,
        deficiency = deficiency,
        depth = depth,
        proposalsInPeriod = proposalsInPeriod,
        estimatedLines = 100,
        humanAuthorised = humanAuthorised,
        subsystemUnderObservationUntil = observationUntil
    )

    // -- L01 / L02 ------------------------------------------------------------

    @Test
    fun `an incomplete observation is refused before anything counts it`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(request(listOf(observation().copy(environmentFingerprint = ""))))

        assertTrue(outcome is LoopOutcome.Refused)
        assertEquals(LoopStage.OBSERVATION, (outcome as LoopOutcome.Refused).stage)
        assertTrue(outcome.reason.contains("environmentFingerprint"))
    }

    @Test
    fun `evidence without hashes cannot become memory`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(request(listOf(observation(hashes = emptyList()))))

        assertEquals(LoopStage.EVIDENCE, (outcome as LoopOutcome.Refused).stage)
    }

    // -- L03, R(d) ------------------------------------------------------------

    @Test
    fun `a single transient failure is noise and does not reach a proposal`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(request(listOf(observation(frequency = 1))))

        assertEquals(LoopStage.REPRODUCIBILITY, (outcome as LoopOutcome.Refused).stage)
        assertTrue(outcome.reason.contains("treated as noise"))
    }

    @Test
    fun `a safety-critical observation reproduces on its first occurrence`() {
        val verdict = ReproducibilityPredicate.evaluate(
            listOf(observation(severity = ObservationSeverity.SAFETY_CRITICAL, frequency = 1))
        )

        assertTrue(verdict.holds, "waiting for a second leak is not caution")
    }

    @Test
    fun `a broken invariant is reproducible without a frequency threshold`() {
        val verdict = ReproducibilityPredicate.evaluate(
            listOf(observation(frequency = 1, invariant = "territory"))
        )

        assertTrue(verdict.holds)
        assertTrue(verdict.reason.contains("territory"))
    }

    @Test
    fun `observations of different deficiencies are reported mixed, not partitioned silently`() {
        val verdict = ReproducibilityPredicate.evaluate(
            listOf(observation(id = "a"), observation(id = "b", environment = "desktop-x86"))
        )

        assertFalse(verdict.holds)
        assertTrue(verdict.reason.contains("more than one deficiency"))
    }

    @Test
    fun `environment independence is recorded separately from R of d`() {
        val sameEnvironment = listOf(observation(id = "a"), observation(id = "b"))

        assertTrue(ReproducibilityPredicate.evaluate(sameEnvironment).holds)
        assertFalse(ReproducibilityPredicate.environmentIndependent(sameEnvironment))
    }

    // -- H01 / H02 ------------------------------------------------------------

    @Test
    fun `a proposal touching territory needs a human`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(
            request(deficiency = deficiency(territory = listOf("src/main/kotlin/atropos/core/territory/")))
        )

        assertTrue(outcome is LoopOutcome.NeedsHuman)
        assertTrue((outcome as LoopOutcome.NeedsHuman).invariants.invariantsTouched.contains("territory"))
    }

    /**
     * The prohibition that matters most: a system that could rewrite what
     * counts as improvement could be argued into anything by first changing the
     * argument.
     */
    @Test
    fun `a proposal that rewrites the improvement predicate is meta-level`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(
            request(deficiency = deficiency(territory = listOf("core/phase20/ImprovementPredicate.kt")))
        )

        val needsHuman = outcome as LoopOutcome.NeedsHuman
        assertTrue(needsHuman.invariants.metaLevel)
        assertTrue(needsHuman.invariants.render().contains("meta-level"))
    }

    @Test
    fun `a human-authorised proposal touching an invariant proceeds`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(
            request(
                deficiency = deficiency(territory = listOf("src/main/kotlin/atropos/core/territory/")),
                humanAuthorised = true
            )
        )

        assertTrue(outcome is LoopOutcome.ContractReady)
    }

    // -- H03 / H04 ------------------------------------------------------------

    @Test
    fun `depth beyond the bound is refused`() {
        val loop = SelfImprovementLoop(ledger(), bounds = SelfImprovementBounds(maxDepth = 2), clock = { at })

        val outcome = loop.advance(request(depth = 3))

        assertEquals(LoopStage.BOUNDS, (outcome as LoopOutcome.Refused).stage)
        assertTrue(outcome.reason.contains("depth 3 exceeds max 2"))
    }

    @Test
    fun `a subsystem under observation cannot be changed again`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(request(observationUntil = at.plus(Duration.ofHours(2))))

        assertEquals(LoopStage.BOUNDS, (outcome as LoopOutcome.Refused).stage)
        assertTrue(outcome.reason.contains("law 20.14"))
    }

    @Test
    fun `every violated bound is reported, not merely the first`() {
        val verdict = SelfImprovementBounds(maxDepth = 1, maxFiles = 1).check(
            BoundsRequest(depth = 9, proposalsInPeriod = 0, files = 9, lines = 99_999, retries = 0, tokensSpentInPeriod = 0, now = at)
        )

        assertFalse(verdict.allowed)
        assertEquals(3, verdict.violations.size)
    }

    @Test
    fun `phone bounds are the same policy with smaller numbers`() {
        val phone = SelfImprovementBounds.phone()

        assertTrue(phone.maxDepth < SelfImprovementBounds.DEFAULT_MAX_DEPTH)
        assertTrue(phone.tokenBudget < SelfImprovementBounds.DEFAULT_TOKEN_BUDGET)
    }

    // -- the happy path stops at a contract -----------------------------------

    @Test
    fun `a clean advance yields a contract and does not execute anything`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(request())

        assertTrue(outcome is LoopOutcome.ContractReady)
        val ready = outcome as LoopOutcome.ContractReady
        assertEquals(DeficiencyClass.RECURRING_FAILURE, ready.deficiency)
        assertTrue(ready.reproducibility.holds)
        assertTrue(ready.render().contains("ready for Phase 11"))
    }

    @Test
    fun `a safety-critical deficiency classifies as such`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })

        val outcome = loop.advance(
            request(listOf(observation(severity = ObservationSeverity.SAFETY_CRITICAL, frequency = 1)))
        )

        assertEquals(DeficiencyClass.SAFETY_CRITICAL, (outcome as LoopOutcome.ContractReady).deficiency)
    }

    // -- L12 / L13 / L14 ------------------------------------------------------

    @Test
    fun `an unverified change is rolled back before its metric is even read`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })
        val proposal = (loop.advance(request()) as LoopOutcome.ContractReady).proposal

        val decision = loop.evaluate(proposal, observedValue = 0.05, verificationPassed = false)

        assertFalse(decision.promote)
        assertTrue(decision.reason.contains("verification failed"))
    }

    @Test
    fun `a verified change that did not improve is rolled back`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })
        val proposal = (loop.advance(request()) as LoopOutcome.ContractReady).proposal

        val decision = loop.evaluate(proposal, observedValue = 0.5, verificationPassed = true)

        assertFalse(decision.promote, "0.5 is worse than the 0.4 baseline on a lower-is-better metric")
        assertTrue(decision.reason.contains("did not move toward target"))
    }

    @Test
    fun `a verified improvement is promoted and reports how far it moved`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })
        val proposal = (loop.advance(request()) as LoopOutcome.ContractReady).proposal

        val decision = loop.evaluate(proposal, observedValue = 0.25, verificationPassed = true)

        assertTrue(decision.promote)
        assertEquals(0.5, decision.improvement.distanceClosed, 0.01)
        assertTrue(decision.reason.contains("learning="))
    }

    /**
     * The conjunct that gets dropped. A change that improves its own metric
     * while degrading another is a trade nobody decided to make.
     */
    @Test
    fun `an improvement that regresses a guardrail is not an improvement`() {
        val loop = SelfImprovementLoop(ledger(), clock = { at })
        val proposal = (loop.advance(request()) as LoopOutcome.ContractReady).proposal

        val decision = loop.evaluate(
            proposal,
            observedValue = 0.1,
            verificationPassed = true,
            guardrailsBefore = listOf(AtroposMetric(MetricId.TERRITORY_SAFETY, 1.0, 100, listOf("h"))),
            guardrailsAfter = listOf(AtroposMetric(MetricId.TERRITORY_SAFETY, 0.8, 100, listOf("h")))
        )

        assertFalse(decision.promote)
        assertTrue(decision.improvement.guardrailRegressions.any { it.contains("territory_safety") })
    }

    @Test
    fun `a lower-is-better guardrail is not reported regressed for falling`() {
        val decision = ImprovementPredicate.evaluate(
            declared = MetricDeclaration("retry_rate", 0.4, 0.1, lowerIsBetter = true),
            observed = 0.1,
            guardrailsBefore = listOf(AtroposMetric(MetricId.COORDINATION_EFFICIENCY, 30_000.0, 10, listOf("h"))),
            guardrailsAfter = listOf(AtroposMetric(MetricId.COORDINATION_EFFICIENCY, 20_000.0, 10, listOf("h")))
        )

        assertTrue(decision.holds, "fewer tokens per change is an improvement, not a regression")
    }

    @Test
    fun `a metric declared without a distinct baseline and target is rejected`() {
        val verdict = ImprovementPredicate.evaluate(
            MetricDeclaration("flat", 0.5, 0.5, lowerIsBetter = false),
            observed = 0.9
        )

        assertFalse(verdict.holds)
        assertTrue(verdict.reason.contains("law 20.6"))
    }

    // -- H06 ------------------------------------------------------------------

    @Test
    fun `the potential is compared lexicographically, not by sum`() {
        val a = TerminationPotential(1, 0, 0, 100)
        val b = TerminationPotential(0, 9, 9, 9_000)

        assertTrue(b < a, "one fewer open deficiency outranks any amount of later components")
    }

    @Test
    fun `a descending step continues and a zero potential terminates`() {
        val step = TerminationRanking.step(
            TerminationPotential(1, 0, 0, 0),
            TerminationPotential(0, 0, 0, 0)
        )

        assertTrue(step.legal)
        assertTrue(step.terminated)
        assertFalse(step.continues)
    }

    @Test
    fun `a rising potential is not a legal transition`() {
        val step = TerminationRanking.step(
            TerminationPotential(1, 0, 0, 0),
            TerminationPotential(2, 0, 0, 0)
        )

        assertFalse(step.legal)
        assertTrue(step.reason.contains("no legal transition raises it"))
    }

    @Test
    fun `repeated stalls stop the loop rather than oscillating`() {
        val same = TerminationPotential(1, 0, 0, 10)

        val first = TerminationRanking.step(same, same, consecutiveStalls = 0)
        val second = TerminationRanking.step(same, same, consecutiveStalls = first.stalls)

        assertTrue(first.continues, "one stall is tolerated for work in flight")
        assertTrue(second.terminated)
        assertTrue(second.reason.contains("oscillation"))
    }

    @Test
    fun `potential components cannot be negative`() {
        assertTrue(runCatching { TerminationPotential(-1, 0, 0, 0) }.isFailure)
    }
}
