/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * `P20-H01` and `P20-H02` — what the loop may never propose, and what it may
 * never propose *about*.
 *
 * > P20-H01 Immutable invariants: human authority · source immutability ·
 * > territory · secrets · paid lock · independent verification · evidence ·
 * > rollback · acceptance · anti-gaming · attestation · safety hard-fails.
 * > IMPL: Any proposal touching these requires human-authorized class.
 *
 * > P20-H02 Meta-level separation: a proposal that rewrites
 * > verification/territory/improvement predicates needs a human gate.
 *
 * These are two different prohibitions and conflating them is the failure this
 * file prevents. The first is about *subject*: a proposal to weaken territory
 * enforcement. The second is about *level*: a proposal to change what counts as
 * an improvement, which is not weakening any invariant but is rewriting the
 * rule by which weakenings are judged. A system that guarded only the first
 * could be argued into anything by first changing the argument.
 *
 * `P20-NS05` states the same separation formally: "system cannot rewrite its own
 * success predicates without external gate". Both prohibitions resolve to the
 * same action — require human authorisation — but they are detected differently
 * and reported separately, because an operator reading a refusal needs to know
 * which line was approached.
 */
object ImmutableInvariants {

    /**
     * The twelve invariants named in `P20-H01`.
     *
     * Held as identifiers rather than prose so a proposal can be checked
     * against them mechanically. The prose lives in the source documents; this
     * is the machine's handle on it.
     */
    val NAMES: Set<String> = setOf(
        "human_authority",
        "source_immutability",
        "territory",
        "secrets",
        "paid_lock",
        "independent_verification",
        "evidence",
        "rollback",
        "acceptance",
        "anti_gaming",
        "attestation",
        "safety_hard_fails"
    )

    /**
     * Terms whose appearance in a proposal's territory or summary suggests it
     * touches an invariant.
     *
     * Deliberately over-broad. A false positive costs a human review; a false
     * negative costs an invariant. The asymmetry is the whole point, and it is
     * why this is a keyword screen rather than a parser — a parser would be
     * more precise and would fail closed less often.
     */
    private val INVARIANT_TERMS: Map<String, String> = mapOf(
        "territory" to "territory",
        "territoryenforcer" to "territory",
        "secret" to "secrets",
        "redaction" to "secrets",
        "vault" to "secrets",
        "paid" to "paid_lock",
        "emergencypaid" to "paid_lock",
        "verifiedcompletiongate" to "independent_verification",
        "independentverification" to "independent_verification",
        "deterministicverifier" to "independent_verification",
        "evidence" to "evidence",
        "rollback" to "rollback",
        "auditor" to "acceptance",
        "antigaming" to "anti_gaming",
        "attestation" to "attestation",
        "contextenvelope" to "attestation",
        "sourcedoc" to "source_immutability",
        "authority" to "human_authority"
    )

    /**
     * Terms that indicate a proposal operates on the improvement machinery
     * itself rather than on the system it improves.
     */
    private val META_TERMS: Map<String, String> = mapOf(
        "reproducibilitypredicate" to "the predicate that decides what is reproducible",
        "improvementpredicate" to "the predicate that decides what counts as improvement",
        "terminationranking" to "the function that decides when the loop stops",
        "immutableinvariants" to "the list of things that may not be changed",
        "proposalgate" to "the gate that accepts proposals",
        "selfimprovementbounds" to "the bounds the loop runs under",
        "classificationcalculator" to "the rule that assigns release classes",
        "metricnormalizer" to "the rule that decides what better means"
    )

    /**
     * Classifies a proposal against both prohibitions.
     *
     * Scans **territory and summary only** — the two fields that describe what
     * the proposal will change. Guardrails and necessity are deliberately
     * excluded, and that exclusion is load-bearing rather than an optimisation:
     * a guardrail naming an invariant is a promise to *preserve* it, and
     * necessity naming one is evidence *about* it. Screening those fields
     * inverts their meaning, so the proposal most careful to state "territory
     * unchanged" would be the one refused for touching territory — which
     * teaches proposers to stop naming what they are protecting.
     *
     * Territory is still authoritative over the summary. A proposal that
     * mentions no invariant in its prose and lists `core/territory/` in its
     * territory is a proposal to change territory enforcement whatever it says
     * about itself.
     */
    fun classify(proposal: ImprovementProposal): InvariantVerdict {
        val haystack = (listOf(proposal.summary) + proposal.territory)
            .joinToString(" ").lowercase().replace(Regex("[^a-z0-9]"), "")

        val touched = INVARIANT_TERMS.filterKeys { haystack.contains(it) }.values.distinct()
        val meta = META_TERMS.filterKeys { haystack.contains(it) }

        return InvariantVerdict(
            invariantsTouched = touched,
            metaLevelReasons = meta.values.toList(),
            requiresHumanAuthorisation = touched.isNotEmpty() || meta.isNotEmpty()
        )
    }

    /** True when [name] is one of the twelve. */
    fun isInvariant(name: String): Boolean = name.trim().lowercase() in NAMES
}

/**
 * Whether a proposal may proceed without a human.
 *
 * The two lists are kept apart rather than merged into a single flag because
 * they call for different reviews: touching an invariant is a question about
 * safety, and touching the meta level is a question about whether the system is
 * being allowed to grade its own homework.
 */
data class InvariantVerdict(
    val invariantsTouched: List<String>,
    val metaLevelReasons: List<String>,
    val requiresHumanAuthorisation: Boolean
) {
    val metaLevel: Boolean get() = metaLevelReasons.isNotEmpty()

    fun render(): String = when {
        !requiresHumanAuthorisation -> "object-level; no immutable invariant implicated"
        metaLevel && invariantsTouched.isNotEmpty() ->
            "meta-level (${metaLevelReasons.first()}) and touches ${invariantsTouched.joinToString(", ")}"
        metaLevel -> "meta-level: ${metaLevelReasons.joinToString("; ")}"
        else -> "touches immutable invariant(s): ${invariantsTouched.joinToString(", ")}"
    }
}
