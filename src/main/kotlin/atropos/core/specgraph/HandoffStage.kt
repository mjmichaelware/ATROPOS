/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

/**
 * The stages of the SpecGraph build line, in the order SpecGraph runs them.
 *
 * SpecGraph's pipeline is `ingest -> extract atoms -> research the atoms ->
 * synthesize a plan -> verify the plan -> export`. ATROPOS consumed exactly one
 * of those stages: [ATOMS], through [atropos.core.factory.SpecGraphAtomizer],
 * which runs `AtomService.extract_document`, prints the atoms and then deletes
 * the database the rest of the pipeline would have built on.
 *
 * Everything downstream was therefore re-derived locally — research by
 * `FactoryResearchService`, the authority graph and DAG by
 * `InternalAuthorityGraphBuilder` and `InternalExecutionDagSynthesizer`, and
 * plan verification not at all, because `PlanningService.verify_plan` has no
 * caller on this side.
 *
 * Naming the stages makes "is ATROPOS using the whole build line?" a question
 * with a checkable answer rather than one that has to be re-established by
 * reading two codebases. [coverageOf] answers it against a bundle.
 */
enum class HandoffStage(
    /** The stage name as the export and the blueprint write it. */
    val canonical: String,
    /**
     * The artifact carrying this stage's output.
     *
     * One artifact per stage, so a missing stage is a missing file rather than
     * a subtly empty section of a present one.
     */
    val carrier: HandoffArtifact
) {
    /** The source documents and their sections, as ingested. */
    SOURCES("sources", HandoffArtifact.SOURCES),

    /** The atoms extracted from those sources. The one stage already in use. */
    ATOMS("atoms", HandoffArtifact.ATOMS),

    /** Per-atom, per-dimension research claims and their evidence. */
    RESEARCH("research", HandoffArtifact.RESEARCH),

    /** The authority graph: atoms and the relations between them. */
    AUTHORITY("authority", HandoffArtifact.AUTHORITY_GRAPH),

    /** The synthesized execution DAG — the three stage nodes per atom. */
    PLAN("plan", HandoffArtifact.EXECUTION_GRAPH),

    /**
     * The verification result over that plan.
     *
     * Carried inside the handoff rather than in a file of its own: `verify_plan`
     * writes the plan's status, and the handoff reproduces it as `plan.status`.
     * A plan whose status is not verified must not be executed, which is the
     * whole reason this stage is listed separately from [PLAN].
     */
    PLAN_VERIFICATION("plan_verification", HandoffArtifact.ATROPOS_HANDOFF),

    /** Requirement-to-node traceability, atom by atom. */
    TRACEABILITY("traceability", HandoffArtifact.TRACEABILITY);

    companion object {

        /**
         * Which stages a bundle actually carries.
         *
         * A stage counts as present only when its carrier is present *and*
         * verified — an artifact that failed its checksum is worse than an
         * absent one, because planning from it would look successful.
         */
        fun coverageOf(present: Set<HandoffArtifact>): Map<HandoffStage, Boolean> =
            entries.associateWith { it.carrier in present }

        /** The stages missing from a bundle, in pipeline order. */
        fun missingFrom(present: Set<HandoffArtifact>): List<HandoffStage> =
            entries.filterNot { it.carrier in present }

        /** True when every stage of the build line is represented. */
        fun complete(present: Set<HandoffArtifact>): Boolean = missingFrom(present).isEmpty()

        /** The coverage as one line, for an evidence record. */
        fun render(present: Set<HandoffArtifact>): String =
            entries.joinToString(" ") { stage ->
                stage.canonical + "=" + if (stage.carrier in present) "present" else "MISSING"
            }
    }
}
