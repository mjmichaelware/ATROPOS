/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuperiorityPrimitivesTest {

    @Test
    fun `ProofCarryingAmendment stores verification details`() {
        val amendment = ProofCarryingAmendment("amend-1", "hash-abc", "sig-123")
        assertEquals("amend-1", amendment.amendmentId)
        assertEquals("hash-abc", amendment.proofHash)
        assertEquals("sig-123", amendment.verifierSignature)
    }

    @Test
    fun `FormalReproducibility computes Rd score`() {
        val obs = RuntimeObservation(
            id = "obs-1",
            timestamp = Instant.now(),
            runtimeId = "run-1",
            projectId = "proj-1",
            goalId = "goal-1",
            nodeId = "node-1",
            authorityFingerprint = "auth-1",
            environmentFingerprint = "env-1",
            exitCode = 1,
            boundedOutput = "err",
            artifactHashes = listOf("hash-1"),
            frequency = 1,
            severity = ObservationSeverity.FAILURE
        )
        
        assertEquals(0.0, FormalReproducibility.evaluate(emptyList()))
        assertEquals(0.1, FormalReproducibility.evaluate(listOf(obs)))
        val manyObs = List(15) { obs }
        assertEquals(1.0, FormalReproducibility.evaluate(manyObs))
    }

    @Test
    fun `MetricSpaceImprovement computes Ip magnitude`() {
        val lowerIp = MetricSpaceImprovement.computeIp(10.0, 8.0, MetricSpaceImprovement.Direction.LOWER_IS_BETTER)
        assertEquals(2.0, lowerIp)

        val higherIp = MetricSpaceImprovement.computeIp(5.0, 9.0, MetricSpaceImprovement.Direction.HIGHER_IS_BETTER)
        assertEquals(4.0, higherIp)

        val negativeIp = MetricSpaceImprovement.computeIp(10.0, 12.0, MetricSpaceImprovement.Direction.LOWER_IS_BETTER)
        assertEquals(-2.0, negativeIp)
    }

    @Test
    fun `ObjectMetaSeparation identifies meta-level territory`() {
        assertTrue(ObjectMetaSeparation.isMetaLevel(listOf("core/phase20/Rules")))
        assertTrue(ObjectMetaSeparation.isMetaLevel(listOf("meta/architecture")))
        assertTrue(ObjectMetaSeparation.isMetaLevel(listOf("core/SelfImprovement")))
        assertFalse(ObjectMetaSeparation.isMetaLevel(listOf("ui/components")))
        assertFalse(ObjectMetaSeparation.isMetaLevel(listOf("core/engine")))
    }

    @Test
    fun `ProposalLattice holds nodes`() {
        val lattice = ProposalLattice(emptyList())
        assertEquals(0, lattice.nodes.size)
    }
}
