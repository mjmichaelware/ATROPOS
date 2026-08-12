/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.verification.GovernedCompileGateResult

/**
 * The four things an operator must be able to see after typing a self-host
 * prompt into the running JAR. Phase 11 acceptance is exactly this chain:
 * natural language routed, source actually mutated, the mutation visible to
 * `git status`, and the compile gate green.
 */
enum class SelfHostRunPredicate(val id: String, val description: String) {
    NL_ROUTED("nl_routed", "natural-language prompt routed to the self-host chain"),
    SOURCE_MUTATED("source_mutated", "expected source outputs exist and are hashed"),
    GIT_STATUS_VISIBLE("git_status_visible", "mutated paths appear in git status"),
    COMPILE_GATE_PASSED("compile_gate_passed", "compile gate observed a zero exit")
}

enum class SelfHostRunVerdict {
    /** Every predicate held. */
    VERIFIED,

    /** The chain ran and stopped honestly short of the full predicate set. */
    PARTIAL
}

/** One mutated path, with the evidence that it is really on disk. */
data class SelfHostMutationProof(
    val path: String,
    val present: Boolean,
    val sha256: String?,
    val gitStatusCode: String?
) {
    fun visibleToGit(): Boolean = !gitStatusCode.isNullOrBlank()

    fun render(): String =
        "$path present=$present sha256=${sha256?.take(16) ?: "none"} git=${gitStatusCode ?: "absent"}"
}

/**
 * The operator-facing proof of one self-host run.
 *
 * [unmetPredicates] is the honest part: a run that could not compile, or that
 * mutated nothing, says so and names which predicate is still false rather than
 * reporting a verdict it did not earn.
 */
data class SelfHostRunProof(
    val goalId: String,
    val mutations: List<SelfHostMutationProof>,
    val compileGate: GovernedCompileGateResult?,
    val gitStatusLines: List<String>,
    val satisfiedPredicates: List<SelfHostRunPredicate>,
    val unmetPredicates: List<SelfHostRunPredicate>,
    val evidenceMarkdownPath: String? = null,
    val evidenceJsonPath: String? = null
) {
    val verdict: SelfHostRunVerdict
        get() = if (unmetPredicates.isEmpty()) SelfHostRunVerdict.VERIFIED else SelfHostRunVerdict.PARTIAL

    fun evidenceLine(): String =
        "self_host_run_proof goal=$goalId verdict=$verdict mutations=${mutations.count { it.present }}/${mutations.size} " +
            "compile=${compileGate?.exitCode?.toString() ?: "none"} " +
            "unmet=${if (unmetPredicates.isEmpty()) "none" else unmetPredicates.joinToString("|") { it.id }}"
}
