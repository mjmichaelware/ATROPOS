package atropos.core.planning

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagDefinition
import atropos.core.dag.DagStore
import java.nio.file.Path

class InternalPlanningGraphService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val ingestionService: InternalIngestionService = InternalIngestionService(),
    private val atomExtractor: InternalAtomExtractor = InternalAtomExtractor(),
    private val authorityGraphBuilder: InternalAuthorityGraphBuilder = InternalAuthorityGraphBuilder(),
    private val executionDagSynthesizer: InternalExecutionDagSynthesizer = InternalExecutionDagSynthesizer(),
    private val store: DagStore = DagStore(repoRoot),
    /**
     * The authoritative atomizer, when one is configured.
     *
     * Consulted before [atomExtractor]. ATROPOS ran the canonical SpecGraph
     * atomizer for years and used it as a checksum -- reading back a count and
     * then planning from its own extractor instead. Every plan that reached
     * execution was second-hand. This is the seam that makes the canonical
     * atoms the ones that actually get executed.
     */
    private val canonicalAtoms: CanonicalAtomProvider = CanonicalAtomProvider.NONE
) {
    fun planFromDocuments(projectId: String, label: String, sources: List<Path>): DagDefinition {
        val atoms = sources.flatMap { source ->
            val document = ingestionService.ingest(projectId, source)
            atomExtractor.extract(document)
        }
        return persistPlan(projectId, label, atoms)
    }

    fun planFromTexts(
        projectId: String,
        label: String,
        sources: Map<String, String>,
        promptFingerprint: String = "",
        promptSpans: String = ""
    ): DagDefinition {
        require(projectId.isNotBlank() && label.isNotBlank()) {
            "text planning requires a project and label"
        }
        require(promptFingerprint.matches(Regex("prompt-[0-9a-f]{16}"))) {
            "text planning requires a hashed prompt fingerprint"
        }
        require(promptSpans.isNotBlank() && promptSpans != "none") {
            "text planning requires classified prompt spans"
        }
        val atoms = sources.toSortedMap().entries.flatMap { (sourcePath, content) ->
            // Canonical first, internal only on fallback. Per source rather
            // than per plan: one unreadable document must not discard the
            // canonical atoms of the others.
            val canonical = canonicalAtoms.atomsFor(
                projectId = projectId,
                sourcePath = sourcePath,
                content = content,
                promptFingerprint = promptFingerprint,
                promptSpans = promptSpans
            )
            canonical?.atoms ?: run {
                val document = ingestionService.ingestText(projectId, sourcePath, content)
                atomExtractor.extract(document)
            }
        }.map { atom -> atom.copy(promptFingerprint = promptFingerprint, promptSpans = promptSpans) }
        return persistPlan(projectId, label, atoms)
    }

    private fun persistPlan(projectId: String, label: String, atoms: List<InternalAtom>): DagDefinition {
        val authorityGraph = authorityGraphBuilder.build(projectId, atoms)
        val draft = executionDagSynthesizer.synthesize(projectId, label, authorityGraph, repoRoot)
        return store.createDag(draft.label, draft.nodes, draft.projectId)
    }
}
