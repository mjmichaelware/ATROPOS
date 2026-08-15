/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

/**
 * Derives the edges the internal planner was not producing.
 *
 * [InternalAtomExtractor.extract] sets `dependencies` only from phrases like
 * "depends on X" appearing literally in the source text. A generated
 * requirements document contains no such phrases, so every atom came out with an
 * empty dependency list and the synthesized DAG had no edges whatsoever.
 *
 * Edges come from [AtomStage]: within a section, implementation waits on the
 * contract and verification waits on the implementation. That is the same shape
 * SpecGraph's planner emits, arrived at from the dimensions this side already
 * has, so a plan built by either route orders the same way.
 *
 * Scoped to the section deliberately. Two sections of a document describe
 * different requirements and have no inherent order; joining them would
 * serialise work that can genuinely run in parallel and would invent a
 * dependency the source never stated.
 */
object InternalAtomDependencyModel {

    /**
     * [atoms] with their stage dependencies filled in.
     *
     * Textual dependencies already present are kept and merged rather than
     * replaced — those came from the source stating an order explicitly, which
     * outranks anything inferred from structure.
     *
     * Only ids present in [atoms] are emitted. A dependency on an atom that was
     * never created would block its dependant forever, which is worse than the
     * missing edge it was meant to add.
     */
    fun withStageDependencies(atoms: List<InternalAtom>): List<InternalAtom> {
        val known = atoms.map { it.id }.toSet()
        val bySectionAndStage: Map<Pair<String, AtomStage>, List<String>> = atoms
            .groupBy { it.sectionId to AtomStage.of(it.dimension) }
            .mapValues { (_, group) -> group.map { it.id } }

        return atoms.map { atom ->
            val predecessorStage = AtomStage.predecessorOf(AtomStage.of(atom.dimension))
            val inherited = predecessorStage
                ?.let { bySectionAndStage[atom.sectionId to it] }
                .orEmpty()

            // A stage with no atoms in this section is skipped rather than
            // reached past. A section with no contract atom should let its
            // implementation start, not wait on a node that does not exist --
            // but a section whose contract exists must never be bypassed.
            val resolved = inherited.ifEmpty {
                predecessorStage
                    ?.let { AtomStage.predecessorOf(it) }
                    ?.let { bySectionAndStage[atom.sectionId to it] }
                    .orEmpty()
            }

            val merged = (atom.dependencies + resolved)
                .filter { it != atom.id && it in known }
                .distinct()

            if (merged == atom.dependencies) atom else atom.copy(dependencies = merged)
        }
    }

    /**
     * The plan's shape as one line, for evidence.
     *
     * Reports edges as well as nodes because "12 nodes" was the number being
     * recorded while the edge count was zero, and a node count alone cannot
     * distinguish a plan from a pile.
     */
    fun render(atoms: List<InternalAtom>): String {
        val edges = atoms.sumOf { it.dependencies.size }
        val roots = atoms.count { it.dependencies.isEmpty() }
        return "nodes=${atoms.size} edges=$edges roots=$roots " +
            "stages=" + AtomStage.entries.joinToString(",") { stage ->
                stage.name.lowercase() + ":" + atoms.count { AtomStage.of(it.dimension) == stage }
            }
    }
}
