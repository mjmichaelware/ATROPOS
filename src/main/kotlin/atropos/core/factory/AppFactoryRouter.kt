package atropos.core.factory

import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.journal.EventJournalService
import java.nio.file.Path

class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(repoRoot.resolve(".atropos/assets").toFile()),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    /**
     * The planner, wired to the canonical atomizer and the lakehouse.
     *
     * Both seams default to NONE at their own declaration sites, which is
     * correct for a planner used standalone and was wrong here: nothing passed
     * them, so a factory run planned from the internal extractor with no atom
     * context and both integrations were unreachable from any real command.
     * This is the composition point where the engine's actual configuration is
     * stated.
     */
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(
        repoRoot = repoRoot,
        executionDagSynthesizer = atropos.core.planning.InternalExecutionDagSynthesizer(
            atomContext = atropos.data.lakehouse.LakehouseAtomContextProvider()
        ),
        canonicalAtoms = SpecGraphCanonicalAtomProvider(repoRoot = repoRoot)
    ),
    private val journal: EventJournalService = EventJournalService(repoRoot)
) {
    fun plan(prompt: String): FactoryPlan {
        val clean = prompt.trim().ifBlank { "build local app" }
        val projectSpec = projectSpecParser.parse(clean)
        val intent = FactoryPlanHelper.classify(projectSpecParser, clean)
        val steps = FactoryPlanHelper.stepsFor(intent)
        return FactoryPlan(
            id = "factory-${FactoryLineage.sha256(clean).take(16)}",
            prompt = clean,
            intent = intent,
            projectSpec = projectSpec,
            steps = steps,
            paidAllowed = paidGate.status().active != null,
            queuedWork = emptyList(),
            assetFiles = emptyList(),
            memoryRecordId = null
        )
    }

    fun runLocal(prompt: String): FactoryPlan = runLocalInternal(prompt)

    fun runClarified(projectId: String, answers: List<Boolean>): FactoryPlan {
        require(projectId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
            "factory project id is invalid"
        }
        val runRoot = repoRoot.resolve(".atropos/research/factory").resolve(projectId).normalize()
        require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory clarification path escaped repository root"
        }
        val request = FactoryClarificationRequest.load(runRoot)
        val persistedAnswers = FactoryClarificationRequest.loadAnswers(runRoot, request)
        require(persistedAnswers == answers) { "factory clarification answers do not match persisted lineage" }
        return runLocalInternal(FactoryClarificationRequest.loadPrompt(runRoot), projectId, answers)
    }

    private fun runLocalInternal(
        prompt: String,
        projectIdOverride: String? = null,
        clarificationAnswers: List<Boolean> = emptyList()
    ): FactoryPlan {
        val planned = plan(prompt)
        val base = projectIdOverride?.let { planned.copy(id = it) } ?: planned
        val lineage = FactoryLineageFactory.prepare(
            repoRoot,
            base.id,
            base.prompt,
            base.projectSpec,
            runMemory = memory,
            clarificationAnswers = clarificationAnswers
        )
        val orchestrator = FactoryRunOrchestrator(
            repoRoot = repoRoot,
            memory = memory,
            assets = assets,
            projectRegistry = projectRegistry,
            planningGraph = planningGraph,
            journal = journal
        )
        return orchestrator.orchestrateRun(base, lineage)
    }

    fun render(plan: FactoryPlan): String = FactoryPlanHelper.render(plan)
}
