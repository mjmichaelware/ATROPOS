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
    private val store: DagStore = DagStore(repoRoot)
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
            val document = ingestionService.ingestText(projectId, sourcePath, content)
            atomExtractor.extract(document)
        }.map { atom -> atom.copy(promptFingerprint = promptFingerprint, promptSpans = promptSpans) }
        return persistPlan(projectId, label, atoms)
    }

    private fun persistPlan(projectId: String, label: String, atoms: List<InternalAtom>): DagDefinition {
        val authorityGraph = authorityGraphBuilder.build(projectId, atoms)
        val draft = executionDagSynthesizer.synthesize(projectId, label, authorityGraph, repoRoot)
        return store.createDag(draft.label, draft.nodes, draft.projectId)
    }
}
