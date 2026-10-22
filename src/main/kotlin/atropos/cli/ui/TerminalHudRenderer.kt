/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.PrintStream
import atropos.core.supremacy.MerkleTree

data class EngineSixAnswers(
    val problem: String = "",
    val approach: String = "",
    val invariants: String = "",
    val changes: String = "",
    val verification: String = "",
    val confidence: Double = 1.0
)

data class TerminalStatusLineData(
    val activeProject: String,
    val issueId: String,
    val providerName: String,
    val tokensUsed: Int,
    val maxTokens: Int,
    val phiValue: Long,
    val isFrozen: Boolean
)

class TerminalHudRenderer(
    val outputStream: PrintStream = System.out,
    val supportsTrueColor: Boolean = true
) {
    fun renderHeaderRibbon(data: TerminalStatusLineData) {
        val emerald = if (supportsTrueColor) "\u001b[38;2;16;185;129m" else "\u001b[32m"
        val reset = "\u001b[0m"
        val ribbon = "=== ATROPOS HUD | Project: ${data.activeProject} | Issue: ${data.issueId} | Provider: ${data.providerName} | Tokens: ${data.tokensUsed}/${data.maxTokens} | Φ=${data.phiValue} ==="
        outputStream.println("$emerald$ribbon$reset")
    }

    fun renderSixAnswersCompact(answers: EngineSixAnswers) {
        outputStream.println("[6-ANSWERS] P: ${answers.problem.take(30)} | A: ${answers.approach.take(30)} | Conf: ${"%.2f".format(answers.confidence)}")
    }

    fun renderProofTree(merkleTree: MerkleTree) {
        outputStream.println("[MERKLE PROOF TREE] Root: ${merkleTree.rootHashHex.take(8)} (Leaves: ${merkleTree.leafCount})")
        for (leaf in merkleTree.leaves.take(10)) {
            outputStream.println("  ├─ ${leaf.relativePath} [${leaf.sha256Hex.take(8)}]")
        }
    }

    fun updateFooterStatusLine(data: TerminalStatusLineData) {
        val freezeFlag = if (data.isFrozen) " [FROZEN]" else " [ACTIVE]"
        val footer = "[STATUS] Issue: ${data.issueId}$freezeFlag | Tokens: ${data.tokensUsed}/${data.maxTokens} | Lyapunov Φ=${data.phiValue}"
        outputStream.print("\u001b[?25l$footer\u001b[?25h\r")
        outputStream.flush()
    }

    fun clearScreenBuffer() {
        outputStream.print("\u001b[2J\u001b[H")
        outputStream.flush()
    }
}
