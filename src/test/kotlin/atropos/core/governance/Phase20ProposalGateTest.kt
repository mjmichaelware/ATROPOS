/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.governance

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase20ProposalGateTest {

    @Test
    fun `refuses proposals modifying protected governance files without human review`() {
        val tempDir = Files.createTempDirectory("proposal-gate-test")
        val gate = Phase20ProposalGate(tempDir)

        val badProposal = ActionProposal(
            proposalId = "p-1",
            authorAgentId = "agent-alpha",
            rationale = "Self-modify AGENTS.md rules",
            targetPaths = listOf("AGENTS.md"),
            patchDiff = "+ modified law"
        )

        val evalBad = gate.evaluateProposal(badProposal)
        assertFalse(evalBad.isPermitted)
        assertTrue(evalBad.requiresHumanReview)
        assertNotNull(evalBad.rejectionReason)

        val goodProposal = ActionProposal(
            proposalId = "p-2",
            authorAgentId = "agent-alpha",
            rationale = "Fix bug in parser",
            targetPaths = listOf("src/main/kotlin/Parser.kt"),
            patchDiff = "+ fix"
        )
        val evalGood = gate.evaluateProposal(goodProposal)
        assertTrue(evalGood.isPermitted)
        assertFalse(evalGood.requiresHumanReview)

        val approved = gate.approveProposal("p-1", "sig-operator-hex")
        assertTrue(approved)

        tempDir.toFile().deleteRecursively()
    }
}
