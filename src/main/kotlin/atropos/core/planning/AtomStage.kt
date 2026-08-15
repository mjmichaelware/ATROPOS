/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

/**
 * The three stages every requirement passes through, and which stage each
 * dimension belongs to.
 *
 * SpecGraph's planner synthesizes exactly this shape — `CONTRACT ->
 * IMPLEMENTATION -> VERIFICATION` per atom, joined by `MUST_PRECEDE` edges. The
 * internal planner produced no edges at all: [InternalAtomExtractor] never
 * populates `InternalAtom.dependencies`, so every node came out with an empty
 * dependency list and the whole plan was a flat set of simultaneously-ready
 * nodes.
 *
 * A measured factory run produced 12 nodes, 0 edges, all 12 `READY` — including
 * a `tests_acceptance` node that could run before the `functional_contract` node
 * whose output it verifies. A plan in which verification may precede the work is
 * not a plan.
 *
 * The dimensions already encode the staging; nothing was reading it. A contract
 * dimension states what must be true, a producing dimension builds it, and a
 * checking dimension confirms it — so the stage of a dimension is a property of
 * the dimension, and the edges follow from it.
 */
enum class AtomStage {
    /** States what must be true. Nothing precedes it. */
    CONTRACT,

    /** Produces the work. Depends on the contract for its section. */
    IMPLEMENTATION,

    /** Confirms the work. Depends on the implementation for its section. */
    VERIFICATION;

    companion object {

        /**
         * The stage a dimension belongs to.
         *
         * [AtomDimension.FUNCTIONAL_CONTRACT] and
         * [AtomDimension.DEPENDENCY_CONTRACT] are contracts by name and by
         * role: they say what the section requires, and everything else in the
         * section is an answer to them.
         *
         * The checking dimensions are the five that
         * [InternalExecutionDagSynthesizer] already maps to non-generating
         * actions, plus [AtomDimension.SECURITY_SECRETS]. Keeping the two
         * lists in agreement matters — a dimension that verifies must not be
         * scheduled before the thing it verifies exists, and a dimension that
         * generates must not wait on itself.
         */
        fun of(dimension: AtomDimension): AtomStage = when (dimension) {
            AtomDimension.FUNCTIONAL_CONTRACT,
            AtomDimension.DEPENDENCY_CONTRACT -> CONTRACT

            AtomDimension.TESTS_ACCEPTANCE,
            AtomDimension.SECURITY_SECRETS,
            AtomDimension.TERRITORY_CAPABILITIES,
            AtomDimension.OBSERVABILITY_PROVENANCE,
            AtomDimension.ROLLBACK_FAILURE_EVIDENCE -> VERIFICATION

            else -> IMPLEMENTATION
        }

        /** The stage that must complete before [stage] may begin, or null. */
        fun predecessorOf(stage: AtomStage): AtomStage? = when (stage) {
            CONTRACT -> null
            IMPLEMENTATION -> CONTRACT
            VERIFICATION -> IMPLEMENTATION
        }
    }
}
