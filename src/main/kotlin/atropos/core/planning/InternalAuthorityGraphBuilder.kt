package atropos.core.planning

class InternalAuthorityGraphBuilder {
    fun build(projectId: String, atoms: List<InternalAtom>): AuthorityGraph {
        val atomIds = atoms.map { it.id }.toSet()
        val resolvedAtoms = atoms.map { atom ->
            atom.copy(
                dependencies = atom.dependencies
                    .flatMap { dependency -> resolveDependency(dependency, atom, atoms, atomIds) }
                    .distinct()
            )
        }
        val adjacency = resolvedAtoms.associate { atom -> atom.id to atom.dependencies }
        return AuthorityGraph(
            projectId = projectId,
            atoms = resolvedAtoms,
            adjacency = adjacency,
            cyclesAllowed = true
        )
    }

    fun coverage(atoms: List<InternalAtom>): Map<AtomDimension, Int> =
        AtomDimension.entries.associateWith { dimension -> atoms.count { it.dimension == dimension } }

    private fun resolveDependency(
        rawDependency: String,
        source: InternalAtom,
        atoms: List<InternalAtom>,
        atomIds: Set<String>
    ): List<String> {
        val dependency = rawDependency.trim()
        if (dependency.isBlank()) return emptyList()
        if (dependency in atomIds) return listOf(dependency)

        // Text ingestion commonly identifies a prerequisite by section rather
        // than by the generated atom UUID. Resolve both local section IDs and
        // document-qualified section coordinates before the DAG is synthesized.
        val sectionMatches = atoms.filter { candidate ->
            candidate.sectionId == dependency &&
                candidate.documentId == source.documentId
        }
        if (sectionMatches.isNotEmpty()) return sectionMatches.map { it.id }

        val qualifiedMatches = atoms.filter { candidate ->
            "${candidate.documentId}:${candidate.sectionId}" == dependency
        }
        return qualifiedMatches.map { it.id }
    }
}
