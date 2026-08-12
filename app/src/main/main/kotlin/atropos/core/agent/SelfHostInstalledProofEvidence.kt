/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * Decides whether a goal's evidence can support an installed-runtime proof claim.
 *
 * A bundle that exports cleanly is not the same as a bundle that proves anything.
 * The exporter's job is to write down what happened; this owner's job is to say
 * whether what happened is enough. Without that separation an empty run and a
 * fully proved run both produce a well-formed `bundle.md`, and the difference is
 * invisible to anyone reading it.
 *
 * The four parts below are load-bearing for the C1 self-build claim, and each is
 * required because dropping it leaves a specific hole:
 *  - **build** — without a zero-exit candidate build there is no evidence the
 *    mutated source even compiles, so `VERIFIED` would rest on nothing.
 *  - **gate** — without a completion-gate report the promotion was never
 *    independently checked, which is self-approval by another name.
 *  - **git status** — without post-mutation status the claim "a real file changed
 *    on disk" has no witness; a sandbox run and a real mutation look identical.
 *  - **swap** — without the jar swap the proof stops short of the installed
 *    runtime, which is the part C1 is actually about.
 *
 * Absence is reported as typed missing parts rather than a boolean so an operator
 * reading a refusal learns which proof to rerun, not merely that something failed.
 */
class SelfHostInstalledProofEvidence {

    fun assess(evidence: List<String>): SelfHostInstalledProofAssessment {
        val missing = SelfHostInstalledProofPart.entries.filterNot { part ->
            evidence.any { part.isSatisfiedBy(it) }
        }
        return SelfHostInstalledProofAssessment(missing)
    }

    fun evidenceLine(assessment: SelfHostInstalledProofAssessment): String =
        "installed_proof complete=${assessment.complete} missing=" +
            assessment.missing.joinToString(",") { it.name }.ifBlank { "none" }
}

data class SelfHostInstalledProofAssessment(
    val missing: List<SelfHostInstalledProofPart>
) {
    val complete: Boolean get() = missing.isEmpty()
}

enum class SelfHostInstalledProofPart(
    private val marker: String,
    private val requiredSubstring: String?
) {
    /** A candidate build that actually succeeded. `ok=false` is a record of failure, not proof. */
    CANDIDATE_BUILD("candidate_jar_build", "ok=true"),

    /** An independent completion-gate report. */
    COMPLETION_GATE("promotion_gate", null),

    /** Post-mutation working-tree status naming what changed. */
    GIT_STATUS("git_status_short", null),

    /** The swap that put the candidate in front of the installed runtime. */
    JAR_SWAP("jar_swap", "promoted=true");

    fun isSatisfiedBy(entry: String): Boolean =
        entry.contains(marker, ignoreCase = true) &&
            (requiredSubstring == null || entry.contains(requiredSubstring, ignoreCase = true))
}
