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

    fun planFromTexts(projectId: String, label: String, sources: Map<String, String>): DagDefinition {
        val atoms = sources.entries.flatMap { (sourcePath, content) ->
            val document = ingestionService.ingestText(projectId, sourcePath, content)
            atomExtractor.extract(document)
        }
        return persistPlan(projectId, label, atoms)
    }

    private fun persistPlan(projectId: String, label: String, atoms: List<InternalAtom>): DagDefinition {
        val authorityGraph = authorityGraphBuilder.build(projectId, atoms)
        val draft = executionDagSynthesizer.synthesize(projectId, label, authorityGraph, repoRoot)
        return store.createDag(draft.label, draft.nodes, draft.projectId)
    }
}
