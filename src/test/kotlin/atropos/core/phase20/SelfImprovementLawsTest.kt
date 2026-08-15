/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.AtroposMetric
import atropos.core.evaluation.MetricId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfImprovementLawsTest {

    private fun mockProposal(summary: String, territory: List<String> = emptyList()): ImprovementProposal {
        return ImprovementProposal(
            id = "prop-1",
            proposedBy = "proposer-1",
            summary = summary,
            necessity = listOf("hash-1"),
            baseline = "base",
            target = "target",
            guardrails = listOf("guardrail-1"),
            territory = territory,
            risk = "low",
            rollback = "revert",
            metric = MetricDeclaration("secret_safety", 1.0, 0.0, true),
            createdAt = Instant.now()
        )
    }

    private fun mockObservation(
        id: String = "obs-1",
        complete: Boolean = true,
        runtimeId: String = "run-1",
        artifactHashes: List<String> = listOf("hash-1"),
        goalId: String? = "goal-1",
        severity: ObservationSeverity = ObservationSeverity.FAILURE
    ): RuntimeObservation {
        return RuntimeObservation(
            id = id,
            timestamp = Instant.now(),
            runtimeId = if (complete) runtimeId else "",
            projectId = "project-1",
            goalId = goalId,
            nodeId = "node-1",
            authorityFingerprint = "auth-1",
            environmentFingerprint = "env-1",
            exitCode = null,
            boundedOutput = "output",
            artifactHashes = artifactHashes,
            frequency = 1,
            severity = severity
        )
    }

    @Test
    fun `Law 20_1 requires human authority when invariants are touched`() {
        val safeProposal = mockProposal("simple refactoring")
        val unsafeProposal = mockProposal("weaken territory checks", territory = listOf("core/territory"))

        assertTrue(SelfImprovementLaws.checkLaw20_1(safeProposal, humanAuthorized = false))
        assertTrue(SelfImprovementLaws.checkLaw20_1(unsafeProposal, humanAuthorized = true))
        assertFalse(SelfImprovementLaws.checkLaw20_1(unsafeProposal, humanAuthorized = false))
    }

    @Test
    fun `Law 20_2 enforces observation completeness`() {
        assertTrue(SelfImprovementLaws.checkLaw20_2(mockObservation()))
        assertFalse(SelfImprovementLaws.checkLaw20_2(mockObservation(complete = false)))
        assertFalse(SelfImprovementLaws.checkLaw20_2(mockObservation(id = "")))
    }

    @Test
    fun `Law 20_3 enforces evidence hashes and goalId presence`() {
        assertTrue(SelfImprovementLaws.checkLaw20_3(mockObservation()))
        assertFalse(SelfImprovementLaws.checkLaw20_3(mockObservation(artifactHashes = emptyList())))
        assertFalse(SelfImprovementLaws.checkLaw20_3(mockObservation(goalId = null)))
    }

    @Test
    fun `Law 20_4 evaluates safety-critical or frequency threshold`() {
        // safety-critical does not require frequency threshold
        val critical = mockObservation(severity = ObservationSeverity.SAFETY_CRITICAL)
        assertTrue(SelfImprovementLaws.checkLaw20_4(listOf(critical)))

        // normal failure requires threshold (default evaluated frequency threshold is 3)
        val failure = mockObservation(severity = ObservationSeverity.FAILURE)
        assertFalse(SelfImprovementLaws.checkLaw20_4(listOf(failure)))
    }

    @Test
    fun `Law 20_5 and 20_6 verify proposal fields`() {
        val proposal = mockProposal("refactoring")
        assertTrue(SelfImprovementLaws.checkLaw20_5(proposal))
        assertTrue(SelfImprovementLaws.checkLaw20_6(proposal))
    }

    @Test
    fun `Law 20_7 requires proposer and auditor to be distinct`() {
        assertTrue(SelfImprovementLaws.checkLaw20_7("proposer-1", "auditor-2"))
        assertFalse(SelfImprovementLaws.checkLaw20_7("proposer-1", "proposer-1"))
    }

    @Test
    fun `Law 20_8 checks amendment fields`() {
        val amendment = AuthorityAmendment(
            id = "am-1",
            proposalId = "prop-1",
            sha256 = "manifest-sha",
            supersedes = "doc-1",
            acceptedBy = "human",
            acceptedAt = Instant.now(),
            evidenceHashes = listOf("ev-1")
        )
        assertTrue(SelfImprovementLaws.checkLaw20_8(amendment))
    }

    @Test
    fun `Law 20_9 restricts DAG execution to affected atoms`() {
        val proposal = mockProposal("refactor", territory = listOf("core/engine"))
        assertTrue(SelfImprovementLaws.checkLaw20_9(proposal, listOf("core/engine/Atom1")))
        assertFalse(SelfImprovementLaws.checkLaw20_9(proposal, listOf("core/engine/Atom1", "ui/Atom2")))
        assertFalse(SelfImprovementLaws.checkLaw20_9(proposal, emptyList()))
    }

    @Test
    fun `Law 20_10 ensures Phase 11 is the only mutation path`() {
        assertTrue(SelfImprovementLaws.checkLaw20_10("PHASE_11_SELF_BUILD"))
        assertFalse(SelfImprovementLaws.checkLaw20_10("PHASE_20_DIRECT_EDIT"))
    }

    @Test
    fun `Law 20_11 requires independent verification`() {
        assertTrue(SelfImprovementLaws.checkLaw20_11("agent-a", "agent-b"))
        assertFalse(SelfImprovementLaws.checkLaw20_11("agent-a", "agent-a"))
        assertFalse(SelfImprovementLaws.checkLaw20_11("", "agent-b"))
    }

    @Test
    fun `Law 20_12 enforces fail-closed verification`() {
        assertTrue(SelfImprovementLaws.checkLaw20_12("VERIFIED"))
        assertFalse(SelfImprovementLaws.checkLaw20_12("AMBIGUOUS"))
        assertFalse(SelfImprovementLaws.checkLaw20_12("FAILED"))
    }

    @Test
    fun `Law 20_13 evaluates improvement predicate`() {
        // Mock proposal has target=0.0, baseline=1.0, lowerIsBetter=true
        val proposal = mockProposal("test")
        assertTrue(SelfImprovementLaws.checkLaw20_13(proposal, 0.5)) // Improved
        assertFalse(SelfImprovementLaws.checkLaw20_13(proposal, 1.5)) // Worsened
    }

    @Test
    fun `Law 20_14 enforces promotion based on improvement`() {
        assertTrue(SelfImprovementLaws.checkLaw20_14(improved = true, action = "PROMOTE"))
        assertTrue(SelfImprovementLaws.checkLaw20_14(improved = false, action = "ROLLBACK"))
        assertFalse(SelfImprovementLaws.checkLaw20_14(improved = true, action = "ROLLBACK"))
        assertFalse(SelfImprovementLaws.checkLaw20_14(improved = false, action = "PROMOTE"))
    }

    @Test
    fun `Law 20_15 ensures outcomes are durably recorded`() {
        assertTrue(SelfImprovementLaws.checkLaw20_15("PROMOTION_RECORD"))
        assertTrue(SelfImprovementLaws.checkLaw20_15("ROLLBACK_RECORD"))
        assertFalse(SelfImprovementLaws.checkLaw20_15("LOG_MESSAGE"))
    }

    @Test
    fun `Law 20_16 enforces safety hard-fail`() {
        assertTrue(SelfImprovementLaws.checkLaw20_16(guardrailsBroken = true, action = "ROLLBACK"))
        assertFalse(SelfImprovementLaws.checkLaw20_16(guardrailsBroken = true, action = "PROMOTE"))
        assertTrue(SelfImprovementLaws.checkLaw20_16(guardrailsBroken = false, action = "PROMOTE"))
    }

    @Test
    fun `Law 20_17 enforces evidence-only completion`() {
        assertTrue(SelfImprovementLaws.checkLaw20_17(claimedComplete = true, evidenceHashes = listOf("hash1")))
        assertFalse(SelfImprovementLaws.checkLaw20_17(claimedComplete = true, evidenceHashes = emptyList()))
        assertTrue(SelfImprovementLaws.checkLaw20_17(claimedComplete = false, evidenceHashes = emptyList()))
    }

    @Test
    fun `Law 20_18 enforces anti-gaming metric invariance`() {
        val m1 = MetricDeclaration("metric", 1.0, 0.0, true)
        val m2 = MetricDeclaration("metric", 1.0, 0.0, true)
        val m3 = MetricDeclaration("metric", 1.0, 0.5, true)
        assertTrue(SelfImprovementLaws.checkLaw20_18(m1, m2))
        assertFalse(SelfImprovementLaws.checkLaw20_18(m1, m3))
    }

    @Test
    fun `Law 20_19 requires agent signature attestation`() {
        assertTrue(SelfImprovementLaws.checkLaw20_19("0123456789abcdef0123456789abcdef")) // 32 chars
        assertFalse(SelfImprovementLaws.checkLaw20_19("short-sig"))
        assertFalse(SelfImprovementLaws.checkLaw20_19(""))
    }

    @Test
    fun `Law 20_20 requires hash-pinned dependencies`() {
        assertTrue(SelfImprovementLaws.checkLaw20_20(listOf("pkg:github/repo@sha256:1234")))
        assertFalse(SelfImprovementLaws.checkLaw20_20(listOf("pkg:github/repo@latest")))
        assertFalse(SelfImprovementLaws.checkLaw20_20(emptyList()))
    }
}
