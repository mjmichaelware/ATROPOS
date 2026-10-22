/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import atropos.core.supremacy.MerkleLeaf
import atropos.core.supremacy.MerkleTree

class TerminalHudRendererTest {

    @Test
    fun `renders ribbon, answers, proof tree and status line`() {
        val baos = ByteArrayOutputStream()
        val ps = PrintStream(baos)
        val renderer = TerminalHudRenderer(outputStream = ps, supportsTrueColor = true)

        val status = TerminalStatusLineData(
            activeProject = "ATROPOS-PRO",
            issueId = "ISSUE-42",
            providerName = "gemini-pro",
            tokensUsed = 15000,
            maxTokens = 1000000,
            phiValue = 98L,
            isFrozen = false
        )

        renderer.renderHeaderRibbon(status)
        renderer.renderSixAnswersCompact(EngineSixAnswers("Bug in parser", "AST Rewrite", "Zero regression", "+ patch", "Tests green", 0.99))
        renderer.renderProofTree(
            MerkleTree(
                rootHashHex = "abcdef123456",
                leafCount = 1,
                leaves = listOf(MerkleLeaf("src/Main.kt", "12345678", 500L))
            )
        )
        renderer.updateFooterStatusLine(status)
        renderer.clearScreenBuffer()

        val out = baos.toString()
        assertTrue(out.contains("ATROPOS HUD"))
        assertTrue(out.contains("ISSUE-42"))
        assertTrue(out.contains("MERKLE PROOF TREE"))
    }
}
