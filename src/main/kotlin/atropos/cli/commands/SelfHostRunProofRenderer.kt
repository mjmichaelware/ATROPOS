/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.agent.SelfHostRunProof
import atropos.core.agent.SelfHostRunVerdict
import atropos.core.agent.SelfHostInstalledProofEvidence

/**
 * Presentation only for [SelfHostRunProof].
 *
 * The operator typed a prompt into the JAR and is owed the same evidence a
 * reviewer would demand: which files moved, what `git status` says about them,
 * and what the compile gate actually exited with. No decision is made here — an
 * unmet predicate is printed as unmet, never softened.
 */
class SelfHostRunProofRenderer(
    private val maxStatusLines: Int = 12,
    /**
     * Plain by default. This renderer's output goes into evidence bundles and
     * logs as well as onto a terminal, and a seal full of escape codes in a
     * text file is worse than no seal.
     */
    private val theme: atropos.cli.ui.TerminalTheme = atropos.cli.ui.TerminalTheme(
        atropos.cli.config.ConfigurationManager(),
        tierOverride = atropos.cli.ui.design.ColorTier.NONE
    )
) {
    private val sigil = atropos.cli.ui.EvidenceSigil(theme)
    private val installedProof = SelfHostInstalledProofEvidence()
    fun render(proof: SelfHostRunProof): String = buildString {
        // The seal, before the detail.
        //
        // A run ends in hashes nobody reads and nobody can compare at a
        // glance. Drawn as a mark, two runs that agree look identical and two
        // that differ look different immediately -- and the mark is a pure
        // function of the digest, so a screenshot of one is as checkable as
        // the string it came from.
        //
        // Drawn from the mutation the run actually produced, and coloured for
        // verification only when the verdict says verified. A seal that looked
        // authoritative for a PARTIAL run would be the fake attestation
        // AGENTS.md 0.6 exists to prevent.
        proof.mutations.firstNotNullOfOrNull { it.sha256 }?.let { digest ->
            sigil.render(digest, proof.verdict == SelfHostRunVerdict.VERIFIED)
                .forEach { appendLine(it) }
        }
        appendLine("verdict: ${proof.verdict}${if (proof.verdict == SelfHostRunVerdict.PARTIAL) " (predicates still unmet)" else ""}")
        appendLine("predicates:")
        proof.satisfiedPredicates.forEach { appendLine("  [ok]      ${it.id} — ${it.description}") }
        proof.unmetPredicates.forEach { appendLine("  [UNMET]   ${it.id} — ${it.description}") }

        val gate = proof.compileGate
        appendLine(
            if (gate == null) {
                "compile gate: not run"
            } else {
                "compile gate: passed=${gate.passed} exit=${gate.exitCode ?: "none"} command=${gate.commandLine()}"
            }
        )
        if (gate != null && !gate.passed) {
            appendLine("compile gate detail: ${gate.message.lineSequence().first().take(200)}")
        }

        appendLine("mutated sources:")
        if (proof.mutations.isEmpty()) {
            appendLine("  (none declared)")
        } else {
            proof.mutations.forEach { appendLine("  - ${it.render()}") }
        }

        appendLine("git status:")
        if (proof.gitStatusLines.isEmpty()) {
            appendLine("  (clean)")
        } else {
            proof.gitStatusLines.take(maxStatusLines).forEach { appendLine("  $it") }
            val hidden = proof.gitStatusLines.size - maxStatusLines
            if (hidden > 0) appendLine("  ... $hidden more")
        }

        proof.evidenceMarkdownPath?.let { appendLine("evidence markdown: $it") }
        proof.evidenceJsonPath?.let { appendLine("evidence json: $it") }
        val evidenceMarkers = buildList {
            proof.compileGate?.let { add("candidate_jar_build ok=${it.passed}") }
            if (proof.evidenceMarkdownPath != null || proof.evidenceJsonPath != null) add("promotion_gate")
            if (proof.gitStatusLines.isNotEmpty()) add("git_status_short")
        }
        appendLine(installedProof.evidenceLine(installedProof.assess(evidenceMarkers)))
    }.trimEnd()
}
