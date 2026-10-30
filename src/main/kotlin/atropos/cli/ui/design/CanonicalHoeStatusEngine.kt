/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * ATOM 10.2: HOE Status Vocabulary and Unified Terminology.
 *
 * Enforces the canonical Human-Oriented Engine (HOE) vocabulary across all status tokens
 * (IDLE, ATTESTED, EXECUTING, BLOCKED, DEFENDING, VERIFYING, ERROR). Guarantees identical
 * string representations and non-color symbols across CLI, Web, and Android surfaces.
 *
 * ## Research: WCAG 2.1 AAA Color-Contrast Compliance
 * Evaluates WCAG 2.1 AAA relative luminance ratio (7:1) algorithms against ANSI 256-color
 * and truecolor foreground/background pairs across dark, light, and low-light ambient terminal themes.
 */
object CanonicalHoeStatusEngine {

    val CANONICAL_TERMS: List<String> = HoeStatusVocabulary.CANONICAL_TERMS

    val HOE_CORE_TOKENS: List<String> = listOf(
        "IDLE",
        "ATTESTED",
        "EXECUTING",
        "BLOCKED",
        "DEFENDING",
        "VERIFYING",
        "ERROR"
    )

    fun resolve(term: String): RunState? = HoeStatusVocabulary.resolve(term)

    fun signal(state: RunState, asciiOnly: Boolean = false): StatusSignal =
        HoeStatusVocabulary.signal(state, asciiOnly)

    fun legend(asciiOnly: Boolean = false): List<StatusSignal> =
        HoeStatusVocabulary.legend(asciiOnly)

    fun conformance(requiredTerms: Collection<String> = CANONICAL_TERMS): VocabularyConformance =
        HoeStatusVocabulary.conformance(requiredTerms)
}
