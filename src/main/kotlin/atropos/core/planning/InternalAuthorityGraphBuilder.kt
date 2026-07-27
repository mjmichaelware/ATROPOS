package atropos.core.planning

class InternalAuthorityGraphBuilder {
    fun build(projectId: String, atoms: List<InternalAtom>): AuthorityGraph {
        val atomIds = atoms.map { it.id }.toSet()
        val adjacency = atoms.associate { atom ->
            atom.id to atom.dependencies.filter { dependency -> dependency in atomIds }
        }
        return AuthorityGraph(
            projectId = projectId,
            atoms = atoms,
            adjacency = adjacency,
            cyclesAllowed = true
        )
    }

    fun coverage(atoms: List<InternalAtom>): Map<AtomDimension, Int> =
        AtomDimension.entries.associateWith { dimension -> atoms.count { it.dimension == dimension } }
}
