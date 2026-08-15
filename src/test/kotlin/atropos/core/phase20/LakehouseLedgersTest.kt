/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LakehouseLedgersTest {

    private val now = Instant.parse("2026-08-14T12:00:00Z")

    private fun tempStore() = EvidenceStore(Files.createTempDirectory("atropos-cas-test-"))

    @Test
    fun `evidence CAS ledger stores and retrieves raw strings`() {
        val ledger = EvidenceCasLedger(tempStore())
        val hash = ledger.store("some-raw-evidence-text")
        assertEquals(64, hash.length)
        assertEquals("some-raw-evidence-text", ledger.get(hash))
    }

    @Test
    fun `observation CAS ledger stores and retrieves runtime observations`() {
        val ledger = ObservationCasLedger(tempStore())
        val obs = RuntimeObservation(
            id = "obs-1",
            timestamp = now,
            runtimeId = "runtime-test",
            projectId = "atropos",
            goalId = "goal-abc",
            nodeId = "node-def",
            authorityFingerprint = "auth-123",
            environmentFingerprint = "env-456",
            exitCode = 0,
            boundedOutput = "output-text",
            artifactHashes = listOf("hash1", "hash2"),
            frequency = 5,
            severity = ObservationSeverity.DEGRADED,
            invariantBroken = "invariant-x",
            requirementBlocked = "req-y"
        )
        val hash = ledger.store(obs)
        val retrieved = ledger.get(hash)
        assertNotNull(retrieved)
        assertEquals(obs.id, retrieved.id)
        assertEquals(obs.timestamp, retrieved.timestamp)
        assertEquals(obs.runtimeId, retrieved.runtimeId)
        assertEquals(obs.projectId, retrieved.projectId)
        assertEquals(obs.goalId, retrieved.goalId)
        assertEquals(obs.nodeId, retrieved.nodeId)
        assertEquals(obs.authorityFingerprint, retrieved.authorityFingerprint)
        assertEquals(obs.environmentFingerprint, retrieved.environmentFingerprint)
        assertEquals(obs.exitCode, retrieved.exitCode)
        assertEquals(obs.boundedOutput, retrieved.boundedOutput)
        assertEquals(obs.artifactHashes, retrieved.artifactHashes)
        assertEquals(obs.frequency, retrieved.frequency)
        assertEquals(obs.severity, retrieved.severity)
        assertEquals(obs.invariantBroken, retrieved.invariantBroken)
        assertEquals(obs.requirementBlocked, retrieved.requirementBlocked)
    }

    @Test
    fun `proposal CAS ledger stores and retrieves proposals`() {
        val ledger = ProposalCasLedger(tempStore())
        val prop = ImprovementProposal(
            id = "prop-1",
            proposedBy = "planner",
            summary = "Refactor components",
            necessity = listOf("evidence-hash-1"),
            baseline = "score=0.5",
            target = "score=0.8",
            guardrails = listOf("no regression"),
            territory = listOf("src/main"),
            risk = "low",
            rollback = "git revert",
            metric = MetricDeclaration("test_metric", 0.5, 0.8, false),
            createdAt = now,
            state = ProposalState.OPEN,
            failureCount = 0
        )
        val hash = ledger.store(prop)
        val retrieved = ledger.get(hash)
        assertNotNull(retrieved)
        assertEquals(prop.id, retrieved.id)
        assertEquals(prop.proposedBy, retrieved.proposedBy)
        assertEquals(prop.summary, retrieved.summary)
        assertEquals(prop.necessity, retrieved.necessity)
        assertEquals(prop.baseline, retrieved.baseline)
        assertEquals(prop.target, retrieved.target)
        assertEquals(prop.guardrails, retrieved.guardrails)
        assertEquals(prop.territory, retrieved.territory)
        assertEquals(prop.risk, retrieved.risk)
        assertEquals(prop.rollback, retrieved.rollback)
        assertEquals(prop.metric.name, retrieved.metric.name)
        assertEquals(prop.metric.baselineValue, retrieved.metric.baselineValue)
        assertEquals(prop.metric.targetValue, retrieved.metric.targetValue)
        assertEquals(prop.metric.lowerIsBetter, retrieved.metric.lowerIsBetter)
        assertEquals(prop.createdAt, retrieved.createdAt)
        assertEquals(prop.state, retrieved.state)
        assertEquals(prop.failureCount, retrieved.failureCount)
    }

    @Test
    fun `amendment CAS ledger stores and retrieves amendments`() {
        val ledger = AmendmentCasLedger(tempStore())
        val amd = AuthorityAmendment(
            id = "amd-1",
            proposalId = "prop-1",
            sha256 = "amendment-content-hash",
            supersedes = "superseded-authority-hash",
            acceptedBy = "operator",
            acceptedAt = now,
            evidenceHashes = listOf("ev-1", "ev-2")
        )
        val hash = ledger.store(amd)
        val retrieved = ledger.get(hash)
        assertNotNull(retrieved)
        assertEquals(amd.id, retrieved.id)
        assertEquals(amd.proposalId, retrieved.proposalId)
        assertEquals(amd.sha256, retrieved.sha256)
        assertEquals(amd.supersedes, retrieved.supersedes)
        assertEquals(amd.acceptedBy, retrieved.acceptedBy)
        assertEquals(amd.acceptedAt, retrieved.acceptedAt)
        assertEquals(amd.evidenceHashes, retrieved.evidenceHashes)
    }
}
