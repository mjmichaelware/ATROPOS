/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.orchestration.AutonomousWaveOrchestrator
import atropos.core.orchestration.WaveExecutionRequest
import atropos.core.orchestration.WaveExecutionResult
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.journal.EventJournalService
import atropos.core.factory.FactoryLiveRepairAction
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Path
import java.time.Instant

/**
 * AppFactoryRouter rewritten for autonomous wave execution.
 *
 * plan() converts a prompt into a typed WaveExecutionRequest.
 * runLocal() delegates synchronously to AutonomousWaveOrchestrator.execute().
 * Zero template paths remain reachable.
 */
class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(repoRoot.resolve(".atropos/assets").toFile()),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(
        repoRoot = repoRoot,
        executionDagSynthesizer = atropos.core.planning.InternalExecutionDagSynthesizer(
            atomContext = atropos.data.lakehouse.LakehouseAtomContextProvider()
        ),
        canonicalAtoms = SpecGraphCanonicalAtomProvider(
            repoRoot = repoRoot,
            onboarding = ProviderOnboardingService()
        )
    ),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val liveRepairAction: FactoryLiveRepairAction = FactoryLiveRepairAction(repoRoot),
    private val orchestrator: AutonomousWaveOrchestrator = AutonomousWaveOrchestrator.createDefault(repoRoot)
) {
    private val runtimeConfig: AtroposConfig = AtroposConfig.load()

    /**
     * Convert a prompt into a typed FactoryPlan.
     * No template generation, no scaffold emission.
     */
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

    fun planWave(prompt: String): WaveExecutionRequest {
        val factoryPlan = plan(prompt)
        val lineage = FactoryLineageFactory.prepare(
            repoRoot,
            factoryPlan.id,
            factoryPlan.prompt,
            factoryPlan.projectSpec,
            runMemory = memory,
            clarificationAnswers = emptyList(),
            localOnly = runtimeConfig.runtime.localOnly
        )

        val targetDir = repoRoot.resolve(".atropos/generated-projects")
            .resolve("${AppProjectGenerator.safeName(factoryPlan.projectSpec.intent.name)}-${factoryPlan.id}")

        return WaveExecutionRequest.fromFactoryPlan(factoryPlan, lineage, targetDir)
    }

    /**
     * Execute a wave request synchronously through the autonomous orchestrator.
     * Throws if the orchestrator fails — no local fallback.
     */
    fun runLocal(prompt: String): Path {
        val request = planWave(prompt)
        val result = orchestrator.execute(request)

        if (!result.success) {
            throw IllegalStateException(
                "Wave execution failed: ${result.message}. " +
                "Completed: ${result.completedAtoms.joinToString(", ")}. " +
                "Failed: ${result.failedAtoms.joinToString(", ")}. " +
                "Blocked: ${result.blockedAtoms.joinToString(", ")}"
            )
        }

        return request.targetDirectory
    }

    fun resume(runId: String): FactoryResumeContext =
        FactoryRunHandoff.readContext(repoRoot, runId)

    fun resume(
        runId: String,
        freeze: FactoryAcceptanceFreeze,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult {
        val handoff = FactoryRunHandoff.read(repoRoot, runId)
        return FactoryObligationLoop(DagStore(repoRoot)).resume(handoff, freeze, executeWave)
    }

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

    fun resumePlan(runId: String): FactoryPlan {
        require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
            "factory project id is invalid"
        }
        val runRoot = repoRoot.resolve(".atropos/research/factory").resolve(runId).normalize()
        require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory resume path escaped repository root"
        }
        val promptPath = runRoot.resolve("user-prompt.md")
        if (!java.nio.file.Files.isRegularFile(promptPath) || java.nio.file.Files.isSymbolicLink(promptPath)) {
            throw IllegalArgumentException(
                "no resumable factory run '$runId' under .atropos/research/factory (missing user-prompt.md); " +
                    "start one with /factory run <prompt>"
            )
        }
        val prompt = FactoryClarificationRequest.loadPrompt(runRoot)
        return runLocalInternal(prompt, projectIdOverride = runId)
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
            clarificationAnswers = clarificationAnswers,
            localOnly = runtimeConfig.runtime.localOnly
        )

        val orchestrator = FactoryRunOrchestrator(
            repoRoot = repoRoot,
            memory = memory,
            assets = assets,
            projectRegistry = projectRegistry,
            planningGraph = planningGraph,
            journal = journal,
            repairVerificationFailure = liveRepairAction::invoke
        )
        return orchestrator.orchestrateRun(base, lineage)
    }

    fun render(plan: FactoryPlan): String = FactoryPlanHelper.render(plan)
}