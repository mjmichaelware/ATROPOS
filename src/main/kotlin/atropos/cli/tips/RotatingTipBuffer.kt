/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.tips

import java.io.File
import java.time.Instant

/**
 * ATOM-TIP-013: Rotating Tip Buffer & In-Flight Context Hints.
 *
 * Cycles through 20 curated tips using a persistent index pointer:
 * TipIndex_{t+1} = (TipIndex_t + 1) mod 20.
 * Emits inline actionable hint cues when traces match failure patterns:
 * (429 rate limit, unresolved reference, dirty working tree, cyclic wait, low mutation kill).
 */
class RotatingTipBuffer(
    private val stateFile: File = File(System.getProperty("java.io.tmpdir"), "atropos_tips.idx")
) {
    companion object {
        const val TOTAL_TIPS = 20

        val CURATED_TIPS = listOf(
            "Press Tab or right arrow to inspect 4-tier trie taxonomy commands and explanations.",
            "Use `/verify narrow` to run atomic verification only over touched workspace files.",
            "Type `?` in command mode to view the full command taxonomy modal cheatsheet.",
            "Use `/agent status` to monitor active concurrency lanes and memory bounds.",
            "Attenuated sandboxes enforce Cap(P_theta) ∩ Cap(A_write) = ∅: model proposals cannot mutate CAS.",
            "Run `/status` anytime to view active token spend, quota consumption, and provider health.",
            "Tree-sitter incremental AST parsing ensures Tier-1 workspaces remain sub-millisecond responsive.",
            "Use `/providers enable <id>` to activate alternative inference providers when rate limits hit.",
            "Rebasing an ephemeral workspace purges all stale receipts, requiring fresh gate proofs.",
            "Zero-entropy attestation bundles cryptographic proofs into verifiable release certificates.",
            "The 500-LOC ceiling is strictly enforced: decomposing large files preserves readability and focus.",
            "Use `/use <provider>` to dynamically switch active AI providers mid-session.",
            "Concolic test generation derives boundary conditions from Z3 solver assertions.",
            "Cross-platform execution routes path resolution through PlatformAdapterRegistry.",
            "Use `/mcp list` to inspect active Model Context Protocol tools and their rate limits.",
            "Mutation testing requires a score >= 0.85 before any synthesis batch can be admitted.",
            "In-process DirectInProcessEngineBridge powers Android ART workflows with zero HTTP network latency.",
            "Use `--web` flag to mirror interactive terminal sessions over binary WebSockets to port 8787.",
            "Exported receipts in ledger E_t form an append-only cryptographic audit trail.",
            "Press Ctrl+T to open a fresh workspace tab or Ctrl+Tab to cycle between active tabs."
        )
    }

    @Synchronized
    fun nextTip(): String {
        val currentIndex = readIndex()
        val tip = CURATED_TIPS[currentIndex % TOTAL_TIPS]
        val nextIndex = (currentIndex + 1) % TOTAL_TIPS
        writeIndex(nextIndex)
        return tip
    }

    fun inferContextualHint(traceMessage: String): String? {
        val lower = traceMessage.lowercase()
        return when {
            "429" in lower || "rate limit" in lower || "too many requests" in lower ->
                "HTTP 429 / Rate Limit: Consider switching active provider via '/use' or reducing concurrent lanes."

            "unresolved reference" in lower || "cannot find symbol" in lower || "unresolved_reference" in lower ->
                "Unresolved Reference: Verify imported packages or run contract lattice synthesizer."

            "dirty working tree" in lower || "uncommitted changes" in lower || "working tree is dirty" in lower ->
                "Dirty Working Tree: Commit or stash pending changes before triggering CAS attestation."

            "cyclic wait" in lower || "deadlock" in lower || "dependency cycle" in lower ->
                "Cyclic Wait: Check DAG edge dependencies to resolve circular blocking conditions."

            "mutation score" in lower || "mutation kill" in lower || "surviving mutants" in lower || "mutationkillrate" in lower ->
                "Mutation Score Alert: Mutation kill rate < 0.85; synthesize stubs targeting escaped mutants."

            else -> null
        }
    }

    private fun readIndex(): Int {
        return try {
            if (stateFile.exists()) {
                stateFile.readText().trim().toIntOrNull() ?: 0
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun writeIndex(index: Int) {
        try {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(index.toString())
        } catch (_: Exception) {
            // Best-effort index persistence
        }
    }
}
