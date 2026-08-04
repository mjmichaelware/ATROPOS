/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * The five completion states, and the rule that they may never collapse.
 *
 * Phase 20 governance candidate `P20-G09` names "completion-state vocabulary
 * collapse" as a deficiency in its own right: a surface that renders
 * IMPLEMENTED, COMPILED, TESTED and VERIFIED as the same green check has told
 * the operator that code exists when what they needed to know is whether it was
 * proven. The first canonical amendment in the same document — nonzero exit
 * forbids VERIFIED — is unenforceable if the vocabulary cannot express the
 * difference in the first place.
 *
 * These are deliberately ordered and deliberately not a superset of
 * [HoeStatusVocabulary]. That vocabulary answers "what is this work doing";
 * this one answers "how far has this claim been proven". A single enum for both
 * is exactly the collapse `P20-G09` forbids.
 */
enum class CompletionState(
    /** The wire form shared by CLI, Web and Android. */
    val canonical: String,
    /** What this state actually establishes, in the operator's terms. */
    val meaning: String,
    /** The non-colour channel Source Doc 3 §E requires alongside any colour. */
    val signal: String
) {
    IMPLEMENTED(
        "implemented",
        "source exists; nothing has been run against it",
        "written"
    ),
    COMPILED(
        "compiled",
        "the source compiles; no test has asserted behaviour",
        "builds"
    ),
    TESTED(
        "tested",
        "tests ran and passed; no independent verifier has agreed",
        "tests pass"
    ),
    VERIFIED(
        "verified",
        "an independent gate agreed, with evidence recorded",
        "verified"
    ),
    BLOCKED(
        "blocked",
        "a gate refused; the claim cannot advance until it is resolved",
        "blocked"
    );

    /**
     * True when this state may be presented as a positive completion claim.
     *
     * Only [VERIFIED] qualifies. [TESTED] is deliberately excluded: a component
     * that ran its own tests and passed them has self-approved, and §0 forbids
     * treating that as completion.
     */
    val isPositiveClaim: Boolean get() = this == VERIFIED

    companion object {
        /** The five terms in proof order, weakest claim first. */
        val ORDER: List<CompletionState> = listOf(IMPLEMENTED, COMPILED, TESTED, VERIFIED, BLOCKED)

        fun fromCanonical(term: String): CompletionState? =
            entries.firstOrNull { it.canonical.equals(term.trim(), ignoreCase = true) }

        /**
         * The strongest state a run's observed facts support.
         *
         * Fail-closed at every step: a missing observation is never read as a
         * pass, so a caller that knows nothing gets [IMPLEMENTED] and a caller
         * whose gate refused gets [BLOCKED] regardless of what else succeeded.
         */
        fun infer(
            compiled: Boolean?,
            tested: Boolean?,
            independentlyVerified: Boolean?,
            blocked: Boolean = false
        ): CompletionState = when {
            blocked -> BLOCKED
            compiled == false || tested == false || independentlyVerified == false -> BLOCKED
            independentlyVerified == true && tested == true && compiled == true -> VERIFIED
            tested == true && compiled == true -> TESTED
            compiled == true -> COMPILED
            else -> IMPLEMENTED
        }
    }
}
